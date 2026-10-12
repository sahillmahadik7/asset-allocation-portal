pipeline {
    agent any

    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
    }

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

    environment {
        TEST_PORT = '8082'
        APP_JAR = 'target/asset-allocation-portal-0.0.1-SNAPSHOT.jar'
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
                archiveArtifacts(
                    artifacts: 'target/*.jar',
                    fingerprint: true
                )
            }
        }

        stage('Start Test Application') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    $jar = Join-Path $PWD.Path "target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar"
                    $java = (Get-Command java.exe -ErrorAction Stop).Source
                    $port = 8082

                    if (-not (Test-Path $jar)) {
                        throw "Application JAR not found: $jar"
                    }

                    $listener = Get-NetTCPConnection `
                        -LocalPort $port `
                        -State Listen `
                        -ErrorAction SilentlyContinue

                    if ($listener) {
                        throw "Port $port is already occupied. Stop the previous test application before retrying."
                    }

                    $env:JENKINS_NODE_COOKIE = "dontKillMe"

                    $app = Start-Process `
                        -FilePath $java `
                        -ArgumentList @(
                            "-Duser.timezone=UTC",
                            "-jar",
                            $jar,
                            "--server.port=$port"
                        ) `
                        -WorkingDirectory $PWD.Path `
                        -RedirectStandardOutput (Join-Path $PWD.Path "selenium-app.log") `
                        -RedirectStandardError (Join-Path $PWD.Path "selenium-app-error.log") `
                        -PassThru

                    $app.Id | Set-Content "test-app.pid"

                    $ready = $false

                    for ($i = 0; $i -lt 60; $i++) {
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
                            Get-Content "selenium-app-error.log" -Tail 40
                            throw "Test application exited before becoming ready."
                        }
                    }

                    if (-not $ready) {
                        Get-Content "selenium-app.log" -Tail 40
                        Get-Content "selenium-app-error.log" -Tail 40
                        throw "Test application did not become ready on port $port."
                    }

                    Write-Host "Test application is ready on port $port."
                '''
            }
        }

        stage('Run Selenium Tests') {
            steps {
                bat 'cd selenium-tests && mvn test "-Dapp.url=http://localhost:8082/"'
            }

            post {
                always {
                    junit(
                        testResults: 'selenium-tests/target/surefire-reports/*.xml',
                        allowEmptyResults: false
                    )
                }
            }
        }

        stage('Stop Test Application') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    if (Test-Path "test-app.pid") {
                        $testPid = Get-Content "test-app.pid" |
                            Select-Object -First 1

                        if ($testPid -match '^\\d+$') {
                            $process = Get-Process `
                                -Id ([int]$testPid) `
                                -ErrorAction SilentlyContinue

                            if ($process) {
                                Stop-Process -Id ([int]$testPid) -Force
                                Write-Host "Stopped test application PID $testPid."
                            }
                        }

                        Remove-Item "test-app.pid" -Force `
                            -ErrorAction SilentlyContinue
                    }
                '''
            }
        }

        stage('Deploy Application') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }

            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    $jar = Join-Path $PWD.Path "target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar"
                    $java = (Get-Command java.exe -ErrorAction Stop).Source
                    $port = [int]$env:APP_PORT

                    if (-not (Test-Path $jar)) {
                        throw "Application JAR not found: $jar"
                    }

                    if ($port -eq 8082) {
                        throw "Deployment port must not be the temporary test port."
                    }

                    $oldPid = $null

                    if (Test-Path "deploy.pid") {
                        $oldPid = Get-Content "deploy.pid" |
                            Select-Object -First 1

                        if ($oldPid -match '^\\d+$') {
                            $oldProcess = Get-Process `
                                -Id ([int]$oldPid) `
                                -ErrorAction SilentlyContinue

                            if ($oldProcess) {
                                Stop-Process -Id ([int]$oldPid) -Force
                                Start-Sleep -Seconds 2
                            }
                        }
                    }

                    $listener = Get-NetTCPConnection `
                        -LocalPort $port `
                        -State Listen `
                        -ErrorAction SilentlyContinue

                    if ($listener) {
                        throw "Port $port is occupied by another process. Check the listener before deploying."
                    }

                    $env:JENKINS_NODE_COOKIE = "dontKillMe"

                    $app = Start-Process `
                        -FilePath $java `
                        -ArgumentList @(
                            "-Duser.timezone=UTC",
                            "-jar",
                            $jar,
                            "--server.port=$port"
                        ) `
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
                            Get-Content "deploy-error.log" -Tail 40
                            throw "Deployed application exited unexpectedly."
                        }
                    }

                    if (-not $ready) {
                        Get-Content "deploy.log" -Tail 40
                        Get-Content "deploy-error.log" -Tail 40
                        throw "Deployment did not become ready on port $port."
                    }

                    Write-Host "Deployment successful at http://localhost:$port/"
                '''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Build, Selenium tests, and deployment completed.'
        }

        failure {
            echo 'FAILURE: Check the failed stage and published test results.'
        }

        always {
            echo 'Pipeline execution finished.'
        }
    }
}
