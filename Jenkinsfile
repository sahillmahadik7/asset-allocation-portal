pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9.16'
    }

    environment {
        APP_JAR = 'target/asset-allocation-portal-0.0.1-SNAPSHOT.jar'
        TEST_PORT = '8082'
        DEPLOY_PORT = '8081'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                bat 'git status --short --branch'
            }
        }

        stage('Build Spring Boot App') {
            steps {
                timeout(time: 3, unit: 'MINUTES') {
                    bat 'mvn -B clean package'
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar',
                    fingerprint: true
            }
        }

        stage('Start Test Application') {
            steps {
                timeout(time: 90, unit: 'SECONDS') {
                    powershell '''
                        $listener = Get-NetTCPConnection `
                            -LocalPort 8082 -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            throw "Port 8082 is already occupied. Stop the previous test application first."
                        }
                    '''

                    bat '''
                        start "AssetTestApp" /B cmd /c "java -Duser.timezone=UTC -jar target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar --server.port=8082 > test-app.log 2>&1"
                    '''

                    powershell '''
                        $ready = $false

                        for ($i = 0; $i -lt 30; $i++) {
                            Start-Sleep -Seconds 2

                            try {
                                $r = Invoke-WebRequest `
                                    -Uri 'http://127.0.0.1:8082/' `
                                    -TimeoutSec 2 -UseBasicParsing

                                if ($r.StatusCode -eq 200) {
                                    $ready = $true
                                    break
                                }
                            } catch {}
                        }

                        if (!$ready) {
                            Get-Content 'test-app.log' `
                                -ErrorAction SilentlyContinue
                            throw 'Test application failed to start on port 8082.'
                        }

                        $listener = Get-NetTCPConnection `
                            -LocalPort 8082 -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            Set-Content 'test-app.pid' $listener.OwningProcess
                        }

                        Write-Host 'Test application is ready on port 8082.'
                    '''
                }
            }
        }

        stage('Run Selenium Tests') {
            steps {
                timeout(time: 4, unit: 'MINUTES') {
                    dir('selenium-tests') {
                        bat 'mvn -B test "-Dapp.url=http://127.0.0.1:8082/"'
                    }
                }
            }

            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'selenium-tests/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Stop Test Application') {
            steps {
                powershell '''
                    if (Test-Path 'test-app.pid') {
                        $rawPid = (Get-Content 'test-app.pid' -Raw).Trim()
                        $appPid = 0

                        if ([int]::TryParse($rawPid, [ref]$appPid)) {
                            $p = Get-CimInstance Win32_Process `
                                -Filter "ProcessId = $appPid" `
                                -ErrorAction SilentlyContinue

                            if ($p -and $p.Name -eq 'java.exe' -and
                                $p.CommandLine -match 'asset-allocation-portal') {
                                Stop-Process -Id $appPid -Force `
                                    -ErrorAction SilentlyContinue
                                Write-Host "Stopped test application PID $appPid."
                            }
                        }

                        Remove-Item 'test-app.pid' -Force `
                            -ErrorAction SilentlyContinue
                    }
                '''
            }
        }

        stage('Deploy Application') {
            steps {
                timeout(time: 90, unit: 'SECONDS') {
                    powershell '''
                        $flag = Join-Path $PWD 'deploy-already-healthy.flag'
                        Remove-Item $flag -Force -ErrorAction SilentlyContinue

                        if (!(Test-Path $env:APP_JAR)) {
                            throw "JAR not found: $env:APP_JAR"
                        }

                        $healthy = $false

                        try {
                            $r = Invoke-WebRequest `
                                -Uri 'http://127.0.0.1:8081/' `
                                -TimeoutSec 3 -UseBasicParsing

                            if ($r.StatusCode -eq 200) {
                                $healthy = $true
                            }
                        } catch {}

                        if ($healthy) {
                            Set-Content $flag 'true'
                            Write-Host 'Application is already healthy on port 8081; reusing it.'
                        } else {
                            $listener = Get-NetTCPConnection `
                                -LocalPort 8081 -State Listen `
                                -ErrorAction SilentlyContinue |
                                Select-Object -First 1

                            if ($listener) {
                                throw 'Port 8081 is occupied but the application is unhealthy. Inspect the process before stopping it.'
                            }
                        }
                    '''

                    bat '''
                        if exist deploy-already-healthy.flag (
                            echo Reusing the healthy application on port 8081.
                        ) else (
                            start "AssetPortalApp" /B cmd /c "java -Duser.timezone=UTC -jar target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar --server.port=8081 > deploy.stdout.log 2>&1"
                        )
                    '''

                    powershell '''
                        $ready = Test-Path 'deploy-already-healthy.flag'

                        for ($i = 0; $i -lt 30 -and !$ready; $i++) {
                            Start-Sleep -Seconds 2

                            try {
                                $r = Invoke-WebRequest `
                                    -Uri 'http://127.0.0.1:8081/' `
                                    -TimeoutSec 2 -UseBasicParsing

                                if ($r.StatusCode -eq 200) {
                                    $ready = $true
                                }
                            } catch {}
                        }

                        if (!$ready) {
                            Get-Content 'deploy.stdout.log' `
                                -ErrorAction SilentlyContinue
                            throw 'Deployment timed out waiting for HTTP 200 on port 8081.'
                        }

                        Write-Host 'Deployment successful: http://localhost:8081/'
                    '''
                }
            }
        }
    }

    post {
        always {
            powershell '''
                $pidFile = Join-Path $PWD 'test-app.pid'

                if (Test-Path $pidFile) {
                    $rawPid = (Get-Content $pidFile -Raw).Trim()
                    $appPid = 0

                    if ([int]::TryParse($rawPid, [ref]$appPid)) {
                        $p = Get-CimInstance Win32_Process `
                            -Filter "ProcessId = $appPid" `
                            -ErrorAction SilentlyContinue

                        if ($p -and $p.Name -eq 'java.exe' -and
                            $p.CommandLine -match 'asset-allocation-portal' -and
                            $p.CommandLine -match '--server.port=8082') {
                            Stop-Process -Id $appPid -Force `
                                -ErrorAction SilentlyContinue
                        }
                    }

                    Remove-Item $pidFile -Force `
                        -ErrorAction SilentlyContinue
                }

                Remove-Item 'deploy-already-healthy.flag' -Force `
                    -ErrorAction SilentlyContinue
            '''
        }

        success {
            echo 'SUCCESS: Build, Selenium tests, and deployment completed.'
        }

        failure {
            echo 'FAILURE: Inspect the failed stage and published test results.'
        }

        aborted {
            echo 'Pipeline was aborted.'
        }
    }
}