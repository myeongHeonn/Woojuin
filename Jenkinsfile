// 우주인 dev 배포 파이프라인 v2 (백엔드 + 프론트엔드)
//
// 트리거: develop push(머지) + develop 대상 MR (GitLab 웹훅)
// 흐름:   변경 경로 감지 → (변경된 파트만) Test → Build → Deploy → Health Check
//
// 배포 대상은 **dev 환경**이다(결정 #16: develop→dev, main→prod).
// prod 배포는 main 용 Job 을 따로 만든다. 이 파이프라인은 prod 를 건드리지 않는다.
//   - 백엔드: dev compose 스택의 backend 컨테이너 교체
//   - 프론트: 호스트 nginx 가 서빙하는 /var/www/woojuin/dev 에 정적 파일 배치
//
// 전제 (Jenkinsfile 만 봐서는 알 수 없는 것들):
//   - Jenkins 가 호스트 docker 를 조종할 수 있어야 한다(DooD, 결정 #22)
//     — docker CLI + /var/run/docker.sock 마운트 + --group-add <docker GID>
//   - 잡 설정 **`Do not allow concurrent builds`** 필수 (아래 고정 태그 주석 참고)
//   - 잡 설정 Branch Specifier = **`${gitlabSourceBranch}`**, 파라미터는 추가하지 말 것
//     (파라미터 기본값이 웹훅 값을 덮어써서 MR 이 항상 develop 을 빌드하게 된다)
//
// ⚠️ DooD 경로 함정: Jenkins 는 컨테이너 안에 있지만 docker 명령은 **호스트 데몬**이 실행한다.
//    그래서 `docker run -v $WORKSPACE:/src` 는 데몬이 호스트에서 그 경로를 못 찾아
//    **빈 디렉토리를 마운트**한다(에러 없이 조용히 실패).
//    거꾸로 **호스트 경로는 유효**하므로, 프론트 배포는 그 성질을 이용한다(Deploy(frontend) 참고).

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

        // 프론트 정적 파일이 놓일 **호스트** 경로. nginx `root` 가 여기를 가리킨다.
        FRONTEND_WEBROOT  = '/var/www/woojuin/dev'

        // 프론트 빌드 시점에 번들에 박히는 값(시크릿 아님 — 공개 API 주소).
        // dev/prod 가 달라야 하므로 prod 용 Job 에서는 다른 값을 쓴다.
        FE_API_BASE_URL   = 'https://api.dev.woojuin.store/api'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm

                // 빌드 시작을 GitLab 에 즉시 알린다.
                // 이게 없으면 post 블록(= 빌드 종료 후)에서야 상태를 보고하므로, 빌드가 도는
                // 2~3분 동안 MR 화면에 "Looks like there's no pipeline here" 가 뜬다.
                // 리뷰어가 "CI 없는 MR"로 착각해 그대로 승인·머지할 수 있다.
                updateGitlabCommitStatus name: 'jenkins', state: 'running'

                script {
                    // 이미지 태그로 쓸 커밋 SHA 앞 7자리.
                    // latest 로만 태깅하면 "지금 뜬 게 어느 커밋인지" 추적이 불가능하고
                    // 롤백 대상도 지정할 수 없다(결정 #9).
                    env.SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    echo "빌드 대상 커밋: ${env.SHORT_SHA}"

                    // 배포 여부를 가르는 값. 실측: MR 이벤트=MERGE / develop push=PUSH.
                    // 플러그인 업그레이드로 값이 바뀌면 여기서 바로 드러나므로 진단용으로 남겨 둔다.
                    echo "gitlabActionType = ${env.gitlabActionType}"
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    // 모노레포라서 프론트만 고쳐도 백엔드 테스트·빌드가 전부 돌면 낭비다.
                    // 동시 빌드를 금지해 뒀으므로 빌드 1회 시간이 곧 다른 MR 의 대기 시간이 되고,
                    // t2 CPU 크레딧도 그만큼 태운다. → 바뀐 파트만 돌린다.

                    // 비교 기준을 정한다. 상황마다 "무엇과 비교해야 맞는가"가 다르다.
                    def base = ''
                    def baseWhy = ''

                    // 머지 커밋인지 판별한다(두 번째 부모가 있으면 머지).
                    def isMerge = sh(
                        script: 'git rev-parse --verify --quiet HEAD^2 > /dev/null',
                        returnStatus: true
                    ) == 0

                    // 🔴 MR 판별은 **gitlabActionType** 으로 한다(실측: MR=MERGE / push=PUSH).
                    //    `gitlabTargetBranch` 존재 여부로 판별하면 안 된다 — 플러그인은
                    //    **push 이벤트에도 이 값을 채운다**(push 는 source=target=develop).
                    //    그러면 push 빌드가 base=origin/develop = HEAD 자신과 비교해
                    //    diff 가 비고 **전 스테이지가 skip 된 채 초록불**이 뜬다(2026-07-28 실제 발생).
                    if (env.gitlabActionType == 'MERGE' && env.gitlabTargetBranch) {
                        // MR 빌드 — 타겟 브랜치와 비교하면 "이 MR 이 가져오는 변경"이 나온다.
                        base = "origin/${env.gitlabTargetBranch}"
                        baseWhy = 'MR 빌드 → 타겟 브랜치'
                    } else if (isMerge) {
                        // develop push 중 **머지 커밋** — 첫 번째 부모(= 머지 전 develop)와 비교하면
                        // "이 머지가 develop 에 가져온 변경"이 정확히 나온다.
                        //
                        // 📌 GIT_PREVIOUS_SUCCESSFUL_COMMIT 을 쓰지 않는 이유: 그 값은 **잡 단위**라
                        //    브랜치를 구분하지 않는다. 직전에 성공한 빌드가 어떤 feature 브랜치의
                        //    MR 빌드였다면 그 커밋이 기준이 되고, 공통 조상까지 거슬러 올라가
                        //    무관한 변경까지 포함돼 불필요한 파트를 빌드한다(낭비. 놓치지는 않는다).
                        base = 'HEAD^1'
                        baseWhy = '머지 커밋 → 머지 전 develop(첫 번째 부모)'
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        // 머지 커밋이 아닌 push(직접 push, squash 머지 등).
                        // ⚠️ 여기서 HEAD^1 을 쓰면 안 된다 — 커밋 여러 개를 한 번에 push 한 경우
                        //    **마지막 커밋만** 잡혀서 앞선 변경을 놓친다.
                        //    넓게 잡히더라도 놓치지 않는 쪽을 택한다.
                        base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                        baseWhy = '일반 push → 지난 성공 빌드(넓게, 안전하게)'
                    }

                    // 🔴 판단 불가일 때는 **전부 빌드**한다.
                    //    "변경 없음"으로 오해해 스킵하면 검증 없이 초록불이 뜨는 사고가 된다.
                    //    (첫 빌드, 지난 성공 빌드 없음, diff 실패 등)
                    def buildAll = {
                        env.CHANGED_BE = 'true'
                        env.CHANGED_FE = 'true'
                    }

                    // 🔴 base 를 "정할 수 있었다"와 "그 base 가 의미 있다"는 다른 문제다.
                    //    base 가 HEAD 자신을 가리키면 diff 는 항상 비고, 그러면 아무것도 검증하지
                    //    않은 채 초록불이 뜬다. 그 경우는 판단 불가로 간주한다.
                    if (base) {
                        def sameAsHead = sh(
                            script: "test \"\$(git rev-parse ${base})\" = \"\$(git rev-parse HEAD)\"",
                            returnStatus: true
                        ) == 0
                        if (sameAsHead) {
                            echo "⚠️ 비교 기준(${base})이 HEAD 와 같은 커밋이다 → 기준 무효 처리"
                            base = ''
                        }
                    }

                    if (!base) {
                        echo '비교 기준을 정할 수 없음(첫 빌드 등) → 전부 빌드'
                        buildAll()
                    } else {
                        // `A...HEAD`(세 점)는 공통 조상 기준 차이 — 기준 브랜치가 앞서 나가도
                        // "내가 바꾼 것"만 잡힌다.
                        // (base 가 HEAD 의 조상인 경우 — 예: HEAD^1 — 두 점과 결과가 같다)
                        def rc = sh(
                            script: "git diff --name-only ${base}...HEAD > .changed-files",
                            returnStatus: true
                        )
                        if (rc != 0) {
                            echo "⚠️ diff 실패 (base=${base}) → 안전하게 전부 빌드"
                            buildAll()
                        } else {
                            def files = readFile('.changed-files').trim()
                            echo "비교 기준: ${base}  (${baseWhy})\n변경된 파일:\n${files ?: '(없음)'}"
                            def lines = files ? files.split('\n') : []

                            env.CHANGED_BE = lines.any { it.startsWith('backend/') } ? 'true' : 'false'
                            env.CHANGED_FE = lines.any { it.startsWith('frontend/') } ? 'true' : 'false'

                            // 파이프라인 자체나 배포 정의가 바뀌면 양쪽을 다 돌려 검증한다.
                            if (lines.any { it == 'Jenkinsfile' || it.startsWith('docker-compose') }) {
                                echo '파이프라인/배포 정의 변경 → 전부 빌드'
                                buildAll()
                            }
                        }
                    }

                    echo "백엔드 빌드: ${env.CHANGED_BE} / 프론트 빌드: ${env.CHANGED_FE}"
                }
            }
        }

        stage('Backend Test') {
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                // backend/Dockerfile 은 `gradle bootJar` 만 실행하고 **테스트를 돌리지 않는다.**
                // 그래서 이 스테이지가 없으면 테스트를 거치지 않은 이미지가 배포된다.
                //
                // 멀티스테이지의 build 스테이지(`FROM gradle:8.8-jdk21 AS build`)만 이미지로 만들어
                // 그 안에서 테스트한다 — 볼륨을 쓰지 않으므로 위의 DooD 경로 함정을 피한다.
                // (`docker build` 자체는 CLI 가 컨텍스트를 데몬에 스트리밍하므로 경로 문제 없음)
                //
                // ⚠️ 태그를 커밋 SHA 로 붙이고 스테이지 끝에서 `docker rmi` 하면
                //    build 스테이지 레이어까지 사라져 **바로 다음 Build Image 가 캐시를 못 쓴다**
                //    (실측: bootJar 가 47초 + 43초로 두 번 돌았다).
                //    그래서 **고정 태그로 덮어쓰고 지우지 않는다** — 레이어가 남아 있어야
                //    ① 다음 스테이지가 재사용하고 ② 다음 빌드도 의존성 레이어를 재사용한다.
                //    이전 빌드의 이미지는 같은 태그를 새로 붙이는 순간 dangling 이 되어
                //    post 의 `docker image prune` 이 정리한다.
                //
                // 🔴 **이 잡은 반드시 `Do not allow concurrent builds` 로 설정돼 있어야 한다.**
                //    고정 태그라서 동시 빌드가 허용되면 두 빌드가 같은 태그를 두고 경쟁한다:
                //      빌드A: build -t ...:cache (A 코드) → 빌드B가 같은 태그를 덮어씀
                //      → 빌드A 의 `docker run ...:cache gradle test` 가 **B의 코드를 테스트**
                //    A가 자기 코드가 아닌 것으로 초록불을 받는, 조용히 잘못되는 사고다.
                //    (Deploy 도 같은 dev 스택을 공유하므로 직렬화가 필요하다)
                sh """
                    docker build --target build \
                        -t woojuin-backend-test:cache \
                        -f backend/Dockerfile backend
                    docker run --rm woojuin-backend-test:cache \
                        gradle test --no-daemon
                """
                // TODO(t2 크레딧): 여기서 더 줄이려면 gradle 캐시를 named volume 으로 유지할 수 있다
                //   (`-v gradle-cache:/home/gradle/.gradle`). named volume 은 호스트 데몬이
                //   관리하므로 DooD 경로 함정과 무관하게 동작한다.
            }
        }

        stage('Backend Build Image') {
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                // 위 Test 가 통과한 커밋만 여기 온다.
                // Test 스테이지가 만든 build 스테이지 레이어를 그대로 재사용하므로
                // (Step 1~6 이 `Using cache` 로 지나가야 정상) 여기서는 실행 이미지만 얹힌다.
                sh """
                    docker build \
                        -t woojuin-backend:${env.SHORT_SHA} \
                        -f backend/Dockerfile backend
                """
            }
        }

        stage('Frontend Test') {
            when { expression { env.CHANGED_FE == 'true' } }
            steps {
                // 프론트 테스트는 **실제 Chromium** 에서 돈다(vitest browser mode + Playwright).
                // 그래서 alpine 을 못 쓰고 공식 Playwright 이미지를 베이스로 한다 — 상세는
                // frontend/Dockerfile 주석 참고.
                //
                // 백엔드와 동일한 이유로 **고정 태그**를 쓰고 지우지 않는다(캐시 보존).
                // 동시 빌드 금지가 전제다.
                sh """
                    docker build --target test \
                        -t woojuin-frontend-test:cache \
                        -f frontend/Dockerfile frontend
                    docker run --rm woojuin-frontend-test:cache \
                        sh -c 'npm run lint && npm test'
                """
                // type-check 는 별도로 돌리지 않는다 — `npm run build` 가 `tsc -b && vite build`
                // 라서 다음 스테이지에서 이미 검증된다.
            }
        }

        stage('Frontend Build') {
            when { expression { env.CHANGED_FE == 'true' } }
            steps {
                // VITE_* 는 런타임이 아니라 **빌드 시점에 번들에 박힌다** → dev 전용 빌드다.
                // (prod 는 main 용 Job 에서 다른 값으로 다시 빌드해야 한다)
                sh """
                    docker build \
                        --build-arg VITE_API_BASE_URL=${FE_API_BASE_URL} \
                        -t woojuin-frontend:${env.SHORT_SHA} \
                        -f frontend/Dockerfile frontend
                """
                // 번들에 실제로 API 주소가 박혔는지 확인한다.
                // 값이 안 들어가도 **빌드는 성공**하고 `undefined/api` 같은 주소가 박히기 때문에
                // (envDir 함정) 이 검사가 없으면 배포 후 프론트가 조용히 API 를 못 찾는다.
                sh """
                    docker run --rm woojuin-frontend:${env.SHORT_SHA} \
                        sh -c 'grep -rqF "${FE_API_BASE_URL}" /dist/assets || (echo "번들에 API 주소가 없다 — VITE_API_BASE_URL 주입 실패"; exit 1)'
                """
            }
        }

        stage('Deploy Backend (dev)') {
            when {
                allOf {
                    // MR 빌드는 검증만 하고 배포하지 않는다. develop 에 머지된 push 일 때만 배포.
                    expression { env.gitlabActionType == null || env.gitlabActionType == 'PUSH' }
                    expression { env.CHANGED_BE == 'true' }
                }
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
                allOf {
                    expression { env.gitlabActionType == null || env.gitlabActionType == 'PUSH' }
                    expression { env.CHANGED_BE == 'true' }
                }
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

        stage('Deploy Frontend (dev)') {
            when {
                allOf {
                    expression { env.gitlabActionType == null || env.gitlabActionType == 'PUSH' }
                    expression { env.CHANGED_FE == 'true' }
                }
            }
            steps {
                // 프론트는 컨테이너로 띄우지 않는다 — 호스트 nginx 가 정적 파일을 직접 서빙한다.
                //
                // 📌 Jenkins 컨테이너에는 호스트의 /var/www 가 보이지 않는다. 그런데
                //    `docker run -v` 의 경로는 **호스트 데몬이 해석**하므로 호스트 경로가 유효하다.
                //    → 산출물이 담긴 이미지를 호스트 웹루트를 마운트한 채로 실행해 복사한다.
                //    (Jenkins 컨테이너에 /var/www 를 추가 마운트할 필요가 없다)
                //
                // ⚠️ 지우고 복사하는 사이 몇 초간 404 가 날 수 있다. 정적 파일의 무중단 교체는
                //    nginx root 를 심볼릭 링크로 두고 새 디렉토리를 만든 뒤 링크만 바꾸는 방식이다.
                //    dev 에는 과하다고 보고 지금은 단순하게 간다(개선 항목으로 문서화됨).
                //
                // `.` 를 붙인 `cp -r /dist/. /out/` 는 dist **내용**을 복사한다
                // (`/dist` 를 붙이면 /out/dist 가 되어 nginx 가 404 를 낸다).
                sh """
                    docker run --rm \
                        -v ${FRONTEND_WEBROOT}:/out \
                        woojuin-frontend:${env.SHORT_SHA} \
                        sh -c 'rm -rf /out/* && cp -r /dist/. /out/ && ls -1 /out | head'
                """
                // nginx 가 실제로 새 파일을 내주는지 확인한다 — 복사만 성공하고 nginx 설정이
                // 어긋나 있으면 404 가 나는데, 그건 배포 성공이 아니다.
                //
                // 별도 curl 이미지를 끌어오지 않고 **방금 만든 이미지(alpine)** 를 재사용한다
                // (busybox wget 이 들어있다). `--network host` 는 호스트 데몬이 해석하므로
                // 호스트의 127.0.0.1:80 = nginx 에 닿는다.
                // DNS 없이 vhost 를 고르려고 Host 헤더를 직접 준다.
                sh """
                    docker run --rm --network host \
                        woojuin-frontend:${env.SHORT_SHA} \
                        wget -q -O /dev/null --header='Host: dev.woojuin.store' http://127.0.0.1/ \
                        || (echo 'nginx 가 dev 프론트를 서빙하지 않는다 — server 블록/root 경로 확인'; exit 1)
                    echo 'nginx 서빙 확인 완료'
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
            // dangling(태그 없는) 이미지 중 **7일 넘은 것만** 정리한다.
            //
            // ⚠️ `until` 필터 없이 `docker image prune -f` 만 돌리면 **방금 이 빌드가 만든
            //    중간 레이어까지 지워진다.** 레거시 빌더는 중간 이미지 기록을 따라가며 캐시를
            //    찾으므로, 그게 사라지면 다음 빌드가 항상 cold start 가 된다.
            //    (실측: `gradle dependencies` 레이어가 매번 재실행 → 빌드당 1분 가까이 낭비.
            //     심지어 레이어 데이터는 태그된 이미지가 붙들고 있어 공간도 별로 안 아꼈다)
            //
            // ⚠️ `docker system prune -a` 는 금지 — 태그 붙은 이미지까지 지워서
            //    롤백 대상 이미지와 jenkins-docker:lts 까지 날릴 수 있다.
            sh 'docker image prune -f --filter "until=168h" || true'
        }
    }
}
