# Copy to set-env.ps1 (git-ignored), fill in, then dot-source it in each PowerShell window:  . .\set-env.ps1
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-mysql-password"
# Dev-only signing key, at least 32 characters. Generate one with:
#   -join ((48..57)+(97..102) | Get-Random -Count 64 | % {[char]$_})
$env:JWT_SECRET  = "change-me-to-a-long-random-string-32+chars"
