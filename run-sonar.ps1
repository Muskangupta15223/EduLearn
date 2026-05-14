param (
    [Parameter(Mandatory=$true, HelpMessage="Please provide your SonarQube token generated from the dashboard.")]
    [string]$Token
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Starting SonarQube Analysis for EduLearn" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Make sure your local SonarQube server is running at http://localhost:9000!" -ForegroundColor Yellow
Write-Host ""

Write-Host "[1/2] Running Maven clean verify with coverage profile..." -ForegroundColor Green
# Using the 'coverage' profile we just created in the pom.xml
mvn clean verify -Pcoverage -DskipTests=false

if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build or tests failed! Sonar analysis might be incomplete. Proceeding anyway..." -ForegroundColor Red
}

Write-Host "[2/2] Running SonarQube analysis..." -ForegroundColor Green
mvn sonar:sonar -Dsonar.login="$Token"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "✅ SonarQube Analysis Complete!" -ForegroundColor Green
    Write-Host "Check your dashboard at http://localhost:9000" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "❌ SonarQube Analysis Failed. Please check the logs above." -ForegroundColor Red
}
