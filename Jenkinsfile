pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9.16'
    }

    parameters {
        string(
            name: 'APP_PORT',
            defaultValue: '8081',
            description: 'Port for the deployed application'
        )
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Spring Boot App') {
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

        stage('Run Selenium Tests') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $jar = Join-Path $PWD.Path "target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar"
                    $java = (Get-Command java.exe -ErrorAction Stop).Source

                    if (-not (Test-Path $jar)) {
                        throw "Application JAR not found: $jar"
                    }

                    $env:JENKINS_NODE_COOKIE = "dontKillMe"

                    $process = Start-Process `
                        -FilePath $java `
                        -ArgumentList @("-Duser.timezone=UTC", "-jar", $jar, "--server.port=8082") `
                        -WorkingDirectory $PWD.Path `
                        -RedirectStandardOutput (Join-Path $PWD.Path "selenium-app.log") `
                        -RedirectStandardError (Join-Path $PWD.Path "selenium-app-error.log") `
                        -PassThru

                    $process.Id | Set-Content "test-app.pid"

                    $ready = $false
                    for ($i = 0; $i -lt 60; $i++) {
                        Start-Sleep -Seconds 2
                        try {
                            $response = Invoke-WebRequest `
                                -Uri "http://localhost:8082/api/assets" `
                                -UseBasicParsing `
                                -TimeoutSec 3

                            if ($response.StatusCode -eq 200) {
                                $ready = $true
                                break
                            }
                        } catch {}

                        $process.Refresh()
                        if ($process.HasExited) {
                            throw "Temporary application stopped. Check selenium-app-error.log"
                        }
                    }

                    if (-not $ready) {
                        throw "Temporary application did not become ready on port 8082. Check selenium-app.log and selenium-app-error.log"
                    }
                '''

                bat 'cd selenium-tests && mvn test -Dapp.url=http://localhost:8082/'
            }

            post {
                always {
                    junit testResults: 'selenium-tests/target/surefire-reports/*.xml',
                          allowEmptyResults: false

                    powershell '''
                        if (Test-Path "test-app.pid") {
                            $testPid = Get-Content "test-app.pid" | Select-Object -First 1
                            if ($testPid -match '^\\d+$') {
                                $p = Get-Process -Id ([int]$testPid) -ErrorAction SilentlyContinue
                                if ($p) {
                                    Stop-Process -Id ([int]$testPid) -Force
                                }
                            }
                            Remove-Item "test-app.pid" -Force -ErrorAction SilentlyContinue
                        }
                    '''
                }
            }
        }

        stage('Deploy Application') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $jar = Join-Path $PWD.Path "target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar"
                    $java = (Get-Command java.exe -ErrorAction Stop).Source
                    $port = [int]$env:APP_PORT

                    if (-not (Test-Path $jar)) {
                        throw "Application JAR not found: $jar"
                    }

                    if (Test-Path "deploy.pid") {
                        $oldPid = Get-Content "deploy.pid" | Select-Object -First 1
                        if ($oldPid -match '^\\d+$') {
                            $oldProcess = Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue
                            if ($oldProcess) {
                                Stop-Process -Id ([int]$oldPid) -Force
                                Start-Sleep -Seconds 2
                            }
                        }
                    }

                    $env:JENKINS_NODE_COOKIE = "dontKillMe"

                    $app = Start-Process `
                        -FilePath $java `
                        -ArgumentList @("-Duser.timezone=UTC", "-jar", $jar, "--server.port=$port") `
                        -WorkingDirectory $PWD.Path `
                        -RedirectStandardOutput (Join-Path $PWD.Path "deploy.log") `
                        -RedirectStandardError (Join-Path $PWD.Path "deploy-error.log") `
                        -PassThru

                    $app.Id | Set-Content "deploy.pid"

                    $ready = $false
                    for ($i = 0; $i -lt 30; $i++) {
                        Start-Sleep -Seconds 2
                        try {
                            $response = Invoke-WebRequest `
                                -Uri "http://localhost:$port/api/assets" `
                                -UseBasicParsing `
                                -TimeoutSec 3

                            if ($response.StatusCode -eq 200) {
                                $ready = $true
                                break
                            }
                        } catch {}

                        $app.Refresh()
                        if ($app.HasExited) {
                            throw "Deployed application stopped. Check deploy-error.log"
                        }
                    }

                    if (-not $ready) {
                        throw "Deployment did not become ready on port $port. Check deploy.log and deploy-error.log"
                    }

                    Write-Host "Deployment successful at http://localhost:$port/"
                '''
            }
        }
    }

    post {
        success {
            echo 'Week 10 pipeline completed: Selenium tests passed and deployment succeeded.'
        }
        failure {
            echo 'Pipeline failed. Check the failed stage, Selenium report, and application logs.'
        }
    }
}