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
$endpoint = if ([string]::IsNullOrWhiteSpace($guildId)) {
    "$baseUrl/commands"
} else {
    "$baseUrl/guilds/$guildId/commands"
}

$headers = @{
    Authorization = "Bot $botToken"
    'User-Agent' = 'DiscordBot (https://woojuin.store, 0.1.0)'
}
$commandFiles = @(
    (Join-Path $PSScriptRoot 'discord-command.json'),
    (Join-Path $PSScriptRoot 'discord-message-command.json')
)
foreach ($commandFile in $commandFiles) {
    $command = Get-Content -LiteralPath $commandFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($guildId)) {
        $command | Add-Member -NotePropertyName integration_types -NotePropertyValue @(0, 1) -Force
        $command | Add-Member -NotePropertyName contexts -NotePropertyValue @(0, 1, 2) -Force
    }
    $body = $command | ConvertTo-Json -Depth 20 -Compress
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $result = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers `
        -ContentType 'application/json; charset=utf-8' -Body $bodyBytes
    Write-Host "Discord 명령 등록 완료: $($result.name) ($($result.id))"
}
if ([string]::IsNullOrWhiteSpace($guildId)) {
    Write-Host '전역 명령은 Discord 전체에 반영되기까지 시간이 걸릴 수 있습니다.'
} else {
    Write-Host "테스트 서버($guildId)에 즉시 사용할 수 있습니다."
}
