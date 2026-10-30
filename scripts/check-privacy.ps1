$root = Split-Path -Parent $PSScriptRoot
$textExtensions = @(
    ".gradle", ".properties", ".json", ".java", ".md", ".ps1", ".sace",
    ".txt", ".yml", ".yaml", ".xml", ".bat", ".cmd", ".sh"
)
$excludedDirectories = @(".gradle", ".idea", ".git", "build", "run", "26.2-0.19.3")
$rootPrefix = $root.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$patterns = [ordered]@{
    "Windows absolute path" = '[A-Za-z]:\\'
    "Windows user path" = '(?i)\\Users\\'
    "Unix user path" = '(?i)/Users/|/home/'
    "Host name marker" = ('DESK' + 'TOP-[A-Z0-9]+')
    "Email address" = '(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'
}

$files = Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
    $relative = $_.FullName.Substring($rootPrefix.Length)
    $segments = $relative -split '[\\/]'
    $excluded = $segments | Where-Object { $_ -in $excludedDirectories }
    -not $excluded -and (
        $_.Extension.ToLowerInvariant() -in $textExtensions -or
        $_.Name -in @(".gitignore", ".gitattributes", "gradlew")
    ) -and
        $_.FullName -ne $PSCommandPath
}

$findings = @()
foreach ($file in $files) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName) {
        $lineNumber++
        foreach ($pattern in $patterns.GetEnumerator()) {
            if ($line -match $pattern.Value) {
                $findings += [pscustomobject]@{
                    File = $file.FullName.Substring($rootPrefix.Length)
                    Line = $lineNumber
                    Type = $pattern.Key
                }
            }
        }
    }
}

if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-String | Write-Error
    Write-Error "Privacy check failed: remove machine-specific or personal data before committing."
    exit 1
}

Write-Output "Privacy check passed: $($files.Count) text files scanned."
exit 0
