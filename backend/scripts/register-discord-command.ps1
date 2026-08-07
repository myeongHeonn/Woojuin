$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $pair = $line -split '=', 2
        $name = $pair[0].Trim()
        $value = $pair[1].Trim().Trim('"').Trim("'")
        if ($name -like 'DISCORD_*' -and [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

$applicationId = $env:DISCORD_APPLICATION_ID
$botToken = $env:DISCORD_BOT_TOKEN
$guildId = $env:DISCORD_GUILD_ID

if ([string]::IsNullOrWhiteSpace($applicationId)) {
    throw 'DISCORD_APPLICATION_ID 환경변수를 입력해주세요.'
}
if ([string]::IsNullOrWhiteSpace($botToken)) {
    throw 'DISCORD_BOT_TOKEN 환경변수를 입력해주세요.'
}

$baseUrl = "https://discord.com/api/v10/applications/$applicationId"
$isGlobal = [string]::IsNullOrWhiteSpace($guildId)
$endpoint = if ($isGlobal) { "$baseUrl/commands" } else { "$baseUrl/guilds/$guildId/commands" }

$headers = @{
    Authorization = "Bot $botToken"
    'User-Agent' = 'DiscordBot (https://woojuin.store, 0.1.0)'
}

# 저장소의 명령 JSON을 그대로 등록 세트로 사용한다.
$commandFiles = @(
    (Join-Path $PSScriptRoot 'discord-command.json'),
    (Join-Path $PSScriptRoot 'discord-message-command.json')
)
$commands = @()
foreach ($commandFile in $commandFiles) {
    $command = Get-Content -LiteralPath $commandFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($isGlobal) {
        # 전역 명령: 서버 설치(0)와 사용자 설치(1), 서버·봇 DM·개인 채널(0,1,2) 모두 허용.
        $command | Add-Member -NotePropertyName integration_types -NotePropertyValue @(0, 1) -Force
        $command | Add-Member -NotePropertyName contexts -NotePropertyValue @(0, 1, 2) -Force
    }
    $commands += $command
}

# PUT은 전체 명령 세트를 통째로 덮어써서(bulk overwrite) 저장소에 없는 옛 명령을 함께 제거한다.
# (POST 방식은 개별 명령만 upsert 하므로 오래전에 등록된 잔여 명령이 남을 수 있다.)
$body = ConvertTo-Json -InputObject @($commands) -Depth 20 -Compress
$bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$result = Invoke-RestMethod -Method Put -Uri $endpoint -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body $bodyBytes

Write-Host '등록된 명령 세트 (저장소 JSON과 동일해야 함):'
foreach ($registered in $result) {
    Write-Host ("  - {0} (id: {1})" -f $registered.name, $registered.id)
}
if ($isGlobal) {
    Write-Host '전역 명령은 Discord 전체에 반영되기까지 최대 1시간가량 걸릴 수 있습니다.'
    Write-Host '반영 확인은 scripts\list-discord-command.ps1 로 실제 등록 상태를 조회하세요.'
} else {
    Write-Host "테스트 서버($guildId)에 즉시 사용할 수 있습니다."
}
