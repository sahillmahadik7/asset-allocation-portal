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
                $listener = Get-NetTCPConnection -LocalPort 8082 `
                    -State Listen -ErrorAction SilentlyContinue |
                    Select-Object -First 1

                if ($listener) {
                    throw "Port 8082 is already occupied. Stop the previous test application before rebuilding."
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
                    Get-Content 'test-app.log' -ErrorAction SilentlyContinue
                    throw 'Test application failed to start on port 8082.'
                }

                $listener = Get-NetTCPConnection -LocalPort 8082 `
                    -State Listen -ErrorAction SilentlyContinue |
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
                $appPid = [int](Get-Content 'test-app.pid' -Raw).Trim()
                $p = Get-CimInstance Win32_Process `
                    -Filter "ProcessId = $appPid" `
                    -ErrorAction SilentlyContinue

                if ($p -and $p.Name -eq 'java.exe' -and
                    $p.CommandLine -match 'asset-allocation-portal') {
                    Stop-Process -Id $appPid -Force
                    Write-Host "Stopped test application PID $appPid."
                }

                Remove-Item 'test-app.pid' -Force
            }
        '''
    }
}


        stage('Deploy Application') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        $port = 8081
                        $jar = Join-Path $PWD 'target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar'
                        $pidFile = Join-Path $PWD 'deploy.pid'
                        $out = Join-Path $PWD 'deploy.stdout.log'
                        $err = Join-Path $PWD 'deploy.stderr.log'

                        if (!(Test-Path $jar)) {
                            throw "Application JAR not found: $jar"
                        }

                        # Stop the previously deployed app only if its PID
                        # file identifies a Java process for this application.
                        if (Test-Path $pidFile) {
                            $oldPid = 0
                            $rawPid = (Get-Content $pidFile -Raw).Trim()

                            if ([int]::TryParse($rawPid, [ref]$oldPid)) {
                                $old = Get-CimInstance Win32_Process `
                                    -Filter "ProcessId = $oldPid" `
                                    -ErrorAction SilentlyContinue

                                if ($old -and $old.Name -eq 'java.exe' -and
                                    $old.CommandLine -match 'asset-allocation-portal') {
                                    Stop-Process -Id $oldPid -Force
                                    Start-Sleep -Seconds 2
                                }
                            }

                            Remove-Item $pidFile -Force
                        }

                        $listener = Get-NetTCPConnection `
                            -LocalPort $port -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            throw "Port 8081 is already occupied. Refusing to stop an unidentified process."
                        }

                        Remove-Item $out, $err `
                            -Force -ErrorAction SilentlyContinue

                        $p = Start-Process -FilePath 'java' `
                            -ArgumentList @(
                                '-Duser.timezone=UTC',
                                '-jar',
                                "`"$jar`"",
                                '--server.port=8081'
                            ) `
                            -PassThru `
                            -RedirectStandardOutput $out `
                            -RedirectStandardError $err

                        Set-Content -Path $pidFile -Value $p.Id

                        $ready = $false
                        for ($i = 0; $i -lt 30; $i++) {
                            Start-Sleep -Seconds 2

                            if ($p.HasExited) {
                                break
                            }

                            try {
                                $response = Invoke-WebRequest `
                                    -Uri 'http://127.0.0.1:8081/' `
                                    -TimeoutSec 3 `
                                    -UseBasicParsing

                                if ($response.StatusCode -eq 200) {
                                    $ready = $true
                                    break
                                }
                            } catch {}
                        }

                        if (!$ready) {
                            Get-Content $out -ErrorAction SilentlyContinue
                            Get-Content $err -ErrorAction SilentlyContinue
                            throw 'Deployment failed: application did not become ready on port 8081.'
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
                        $p = Get-Process -Id $appPid `
                            -ErrorAction SilentlyContinue

                        if ($p) {
                            Stop-Process -Id $appPid -Force `
                                -ErrorAction SilentlyContinue
                        }
                    }

                    Remove-Item $pidFile -Force `
                        -ErrorAction SilentlyContinue
                }
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