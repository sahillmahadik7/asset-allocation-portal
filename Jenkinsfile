pipeline {
    agent any

    parameters {
        string(
            name: 'APP_PORT',
            defaultValue: '8081',
            description: 'Port on which the Asset Allocation Portal will run'
        )
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test') {
            steps {
                bat 'mvn clean package'
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar',
                                   fingerprint: true
            }
        }

        stage('Deploy') {
            steps {
                withEnv(["APP_PORT=${params.APP_PORT}"]) {
                    powershell '''
                        $pidFile = "deploy.pid"
                        $batFile = "$env:WORKSPACE\\start-app.bat"

                        if (Test-Path $pidFile) {
                            $oldPid = Get-Content $pidFile

                            if ($oldPid) {
                                $oldProcess = Get-Process -Id $oldPid -ErrorAction SilentlyContinue

                                if ($oldProcess) {
                                    Stop-Process -Id $oldPid -Force
                                    Write-Host "Stopped previous application process: $oldPid"
                                }
                            }

                            Remove-Item $pidFile -Force
                        }

                        $jar = Get-ChildItem "target\\*.jar" |
                               Where-Object { $_.Name -notmatch "original" } |
                               Select-Object -First 1

                        if (-not $jar) {
                            throw "JAR file not found."
                        }

                        Write-Host "Deploying: $($jar.FullName)"
                        Write-Host "Application port: $env:APP_PORT"

                        $batContent = @"
@echo off
cd /d "$env:WORKSPACE"
start "" /b java -Duser.timezone=UTC -jar "$($jar.FullName)" --server.port=$env:APP_PORT > "$env:WORKSPACE\\deploy.log" 2> "$env:WORKSPACE\\deploy-error.log"
"@

                        Set-Content -Path $batFile -Value $batContent

                        Start-Process `
                            -FilePath "cmd.exe" `
                            -ArgumentList "/c", "`"$batFile`"" `
                            -WorkingDirectory $env:WORKSPACE `
                            -WindowStyle Hidden

                        Start-Sleep -Seconds 5

                        $javaProcess = Get-CimInstance Win32_Process |
                            Where-Object {
                                $_.Name -eq "java.exe" -and
                                $_.CommandLine -like "*asset-allocation-portal-0.0.1-SNAPSHOT.jar*"
                            } |
                            Select-Object -First 1

                        if (-not $javaProcess) {
                            throw "Application process did not start. Check deploy.log and deploy-error.log."
                        }

                        Set-Content $pidFile $javaProcess.ProcessId

                        Write-Host "Application started with PID: $($javaProcess.ProcessId)"
                        Write-Host "Deployment completed. Jenkins can continue."
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline and deployment completed successfully.'
        }

        failure {
            echo 'Pipeline or deployment failed.'
        }
    }
}