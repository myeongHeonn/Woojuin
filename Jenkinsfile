// 우주인 백엔드 파이프라인 v1
//
// 트리거: develop push(머지) + develop 대상 MR (GitLab 웹훅)
// 흐름:   Test → Build Image(커밋 SHA 태깅) → Deploy(dev 스택) → Health Check
//
// 배포 대상은 **dev 스택**이다(결정 #16: develop→dev, main→prod).
// prod 배포는 main 용 Job 을 따로 만든다. 이 파이프라인은 prod 스택을 건드리지 않는다.
//
// 전제: Jenkins 컨테이너가 호스트 docker 를 조종할 수 있어야 한다(DooD, 결정 #22).
//       docker CLI + /var/run/docker.sock 마운트 + --group-add <docker GID>
//
// ⚠️ DooD 경로 함정: Jenkins 는 컨테이너 안에 있지만 docker 명령은 **호스트 데몬**이 실행한다.
//    그래서 `docker run -v $WORKSPACE:/src` 는 데몬이 호스트에서 그 경로를 못 찾아
//    **빈 디렉토리를 마운트**한다(에러 없이 조용히 실패). 이 파일은 볼륨 마운트를 쓰지 않는다.

pipeline {
    agent any

    options {
        // 빌드 이력이 무한정 쌓이면 jenkins-data 가 디스크를 먹는다.
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        gitLabConnection('ssafy-gitlab')
    }

    environment {
        // COMPOSE_FILE / COMPOSE_PROJECT_NAME 은 docker compose 가 실제로 읽는 예약 변수명이라
        // 의도치 않은 동작을 피하려고 다른 이름을 쓴다.
        DEPLOY_FILE       = 'docker-compose.deploy.yml'
        STACK             = 'woojuin-dev'
        BACKEND_CONTAINER = 'woojuin-dev-backend-1'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // 이미지 태그로 쓸 커밋 SHA 앞 7자리.
                    // latest 로만 태깅하면 "지금 뜬 게 어느 커밋인지" 추적이 불가능하고
                    // 롤백 대상도 지정할 수 없다(결정 #9).
                    env.SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    echo "빌드 대상 커밋: ${env.SHORT_SHA}"

                    // ⚠️ 미확정: MR 빌드를 걸러내는 조건으로 이 값을 쓰는데,
                    // GitLab 플러그인 버전에 따라 변수명/값이 다를 수 있다.
                    // 첫 빌드(그리고 MR 빌드) 로그에서 실제 값을 확인하고 아래 when 조건을 맞출 것.
                    echo "gitlabActionType = ${env.gitlabActionType}"
                }
            }
        }

        stage('Test') {
            steps {
                // backend/Dockerfile 은 `gradle bootJar` 만 실행하고 **테스트를 돌리지 않는다.**
                // 그래서 이 스테이지가 없으면 테스트를 거치지 않은 이미지가 배포된다.
                //
                // 멀티스테이지의 build 스테이지(`FROM gradle:8.8-jdk21 AS build`)만 이미지로 만들어
                // 그 안에서 테스트한다 — 볼륨을 쓰지 않으므로 위의 DooD 경로 함정을 피한다.
                // (`docker build` 자체는 CLI 가 컨텍스트를 데몬에 스트리밍하므로 경로 문제 없음)
                sh """
                    docker build --target build \
                        -t woojuin-backend-test:${env.SHORT_SHA} \
                        -f backend/Dockerfile backend
                    docker run --rm woojuin-backend-test:${env.SHORT_SHA} \
                        gradle test --no-daemon
                """
                // TODO(t2 크레딧): 의존성 재다운로드를 줄이려면 gradle 캐시를 named volume 으로
                //   유지할 수 있다(`-v gradle-cache:/home/gradle/.gradle`).
                //   named volume 은 호스트 데몬이 관리하므로 DooD 경로 함정과 무관하게 동작한다.
                //   첫 파이프라인 성공을 먼저 확인한 뒤 붙인다.
            }
            post {
                always {
                    // 테스트용 임시 이미지는 성공/실패와 무관하게 지운다.
                    sh "docker rmi woojuin-backend-test:${env.SHORT_SHA} || true"
                }
            }
        }

        stage('Build Image') {
            steps {
                // 위 Test 가 통과한 커밋만 여기 온다.
                // 레이어 캐시가 살아있어 대부분 재사용되므로 Test 단계와 중복 비용은 작다.
                sh """
                    docker build \
                        -t woojuin-backend:${env.SHORT_SHA} \
                        -f backend/Dockerfile backend
                """
            }
        }

        stage('Deploy (dev)') {
            when {
                // MR 빌드는 검증만 하고 배포하지 않는다. develop 에 머지된 push 일 때만 배포.
                expression { env.gitlabActionType == null || env.gitlabActionType == 'PUSH' }
            }
            steps {
                // Jenkins 컨테이너는 호스트의 ~/woojuin/dev/.env.dev 를 볼 수 없다(마운트 안 함).
                // 변수를 개별 Credential 로 10여 개 등록하는 대신 **파일 통째로 Secret file** 로 올린다.
                // withCredentials 가 임시 파일에 풀어 주고, 빌드가 끝나면 삭제된다(로그에도 안 찍힘).
                withCredentials([file(credentialsId: 'env-dev', variable: 'ENV_FILE')]) {
                    // BACKEND_IMAGE 를 쉘 환경변수로 준다. compose 치환에서 쉘 환경변수가
                    // --env-file 보다 우선하므로 .env.dev 의 값을 이번 커밋 SHA 로 덮어쓴다.
                    sh """
                        BACKEND_IMAGE=woojuin-backend:${env.SHORT_SHA} \
                        docker compose -p ${STACK} \
                            --env-file "\$ENV_FILE" \
                            -f ${DEPLOY_FILE} \
                            up -d
                    """
                }
            }
        }

        stage('Health Check') {
            when {
                expression { env.gitlabActionType == null || env.gitlabActionType == 'PUSH' }
            }
            steps {
                // 호스트 포트(127.0.0.1:8091)로는 Jenkins 컨테이너에서 닿지 않고,
                // 외부 URL 은 nginx 가 /actuator 를 403 으로 막는다(의도된 설정).
                // → 컨테이너가 자기 자신을 찌르게 하면 네트워크 문제가 없다.
                //
                // 재시도하는 이유: 기동 직후에는 Spring 이 Tomcat 을 올리는 중이라 연결이 안 된다
                // (실측: `health: starting` 구간에 빈 응답). compose 의 start_period 와 같은 맥락.
                sh """
                    for i in \$(seq 1 30); do
                        if docker exec ${BACKEND_CONTAINER} \
                            wget -qO- http://localhost:8080/actuator/health 2>/dev/null \
                            | grep -q '"status":"UP"'; then
                            echo "헬스체크 통과 (\${i}회 시도)"
                            exit 0
                        fi
                        echo "대기 중... (\${i}/30)"
                        sleep 5
                    done
                    echo "헬스체크 실패 — 2분 30초 안에 UP 이 되지 않았다"
                    # compose 로 로그를 보려면 --env-file 이 또 필요하고 \${VAR:?} 파싱을 다시 타므로
                    # 컨테이너 이름으로 직접 본다.
                    docker logs --tail=100 ${BACKEND_CONTAINER} || true
                    exit 1
                """
            }
        }
    }

    post {
        success {
            updateGitlabCommitStatus name: 'jenkins', state: 'success'
        }
        failure {
            updateGitlabCommitStatus name: 'jenkins', state: 'failed'
        }
        always {
            // dangling(태그 없는) 이미지만 정리한다.
            // ⚠️ `docker system prune -a` 는 금지 — 태그 붙은 이미지까지 지워서
            //    롤백 대상 이미지와 jenkins-docker:lts 까지 날릴 수 있다.
            sh 'docker image prune -f || true'
        }
    }
}
