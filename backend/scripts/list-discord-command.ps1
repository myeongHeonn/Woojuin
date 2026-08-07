$ErrorActionPreference = 'Stop'

# Discord에 실제 등록된 전역/길드 명령을 조회해 저장소 JSON과 비교하기 위한 읽기 전용 스크립트.
# Bot Token은 어떤 출력에도 남기지 않는다.

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

if ([string]::IsNullOrWhiteSpace($applicationId)) { throw 'DISCORD_APPLICATION_ID 환경변수를 입력해주세요.' }
if ([string]::IsNullOrWhiteSpace($botToken)) { throw 'DISCORD_BOT_TOKEN 환경변수를 입력해주세요.' }

$baseUrl = "https://discord.com/api/v10/applications/$applicationId"
$isGlobal = [string]::IsNullOrWhiteSpace($guildId)
$endpoint = if ($isGlobal) { "$baseUrl/commands" } else { "$baseUrl/guilds/$guildId/commands" }

$headers = @{
    Authorization = "Bot $botToken"
    'User-Agent' = 'DiscordBot (https://woojuin.store, 0.1.0)'
}

$commands = Invoke-RestMethod -Method Get -Uri $endpoint -Headers $headers

$typeName = @{ 1 = 'SUB_COMMAND'; 2 = 'SUB_GROUP'; 3 = 'STRING'; 4 = 'INTEGER'; 11 = 'ATTACHMENT' }
function Show-Options($options, $indent) {
    if ($null -eq $options) { return }
    foreach ($opt in $options) {
        $kind = $typeName[[int]$opt.type]
        if (-not $kind) { $kind = "type=$($opt.type)" }
        Write-Host ("{0}- {1} [{2}]" -f $indent, $opt.name, $kind)
        Show-Options $opt.options ($indent + '  ')
    }
}

Write-Host ("조회 대상: {0}" -f ($(if ($isGlobal) { '전역(global)' } else { "길드 $guildId" })))
Write-Host ("등록된 명령 수: {0}" -f @($commands).Count)
foreach ($command in $commands) {
    Write-Host ''
    Write-Host ("[{0}] (id: {1}, type: {2})" -f $command.name, $command.id, $command.type)
    Show-Options $command.options '  '
}
Write-Host ''
Write-Host '저장소 기준: /woojuin 아래에 help, connect, account, disconnect, workspace(number), save(content,workspace), image(attachment,workspace), search 가 있어야 하고 memo 는 없어야 합니다.'
