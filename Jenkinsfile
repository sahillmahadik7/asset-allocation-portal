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
                script {
                    def appPort = params.APP_PORT ?: '8081'

                    powershell """
                        \\$pidFile = "deploy.pid"

                        if (Test-Path \\$pidFile) {
                            \\$oldPid = Get-Content \\$pidFile

                            if (\\$oldPid) {
                                \\$process = Get-Process -Id \\$oldPid -ErrorAction SilentlyContinue

                                if (\\$process) {
                                    Stop-Process -Id \\$oldPid -Force
                                    Write-Host "Stopped previous application process: \\$oldPid"
                                }
                            }

                            Remove-Item \\$pidFile -Force
                        }

                        \\$jar = Get-ChildItem "target\\\\*.jar" |
                            Where-Object { \\$_.Name -notmatch "original" } |
                            Select-Object -First 1

                        if (-not \\$jar) {
                            throw "JAR file not found."
                        }

                        Write-Host "Deploying: \\$($jar.FullName)"
                        Write-Host "Application port: ${appPort}"

                        \\$process = Start-Process `
                            -FilePath "java" `
                            -ArgumentList "-jar `"$($jar.FullName)`" --server.port=${appPort}" `
                            -WorkingDirectory \\$env:WORKSPACE `
                            -RedirectStandardOutput "\\$env:WORKSPACE\\\\deploy.log" `
                            -RedirectStandardError "\\$env:WORKSPACE\\\\deploy-error.log" `
                            -PassThru

                        Set-Content \\$pidFile \\$process.Id

                        Write-Host "Application started with PID: \\$($process.Id)"
                    """
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