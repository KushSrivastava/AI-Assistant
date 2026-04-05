Write-Host "Checking for Node.js..."
if (!(Get-Command "npx" -ErrorAction SilentlyContinue)) {
    Write-Error "Node.js and npm are not installed! Please install Node from https://nodejs.org/"
    exit 1
}

Write-Host "Generating Angular 19+ Dashboard Workspace..."
npx @angular/cli@latest new knowledge-bot-dashboard --routing true --style css --standalone true --skip-install true --directory knowledge-bot-dashboard --force

Write-Host "--------------------------------------------------------"
Write-Host "Angular scaffold initialized in knowledge-bot-dashboard/ !"
Write-Host "To install dependencies and start the UI later, run:"
Write-Host "cd knowledge-bot-dashboard"
Write-Host "npm install"
Write-Host "npm start"
Write-Host "--------------------------------------------------------"
