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
                timeout(time: 2, unit: 'MINUTES') {
                    powershell '''
                        $ErrorActionPreference = 'Stop'
                        $port = 8082
                        $jar = Join-Path $PWD 'target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar'
                        $out = Join-Path $PWD 'test-app.stdout.log'
                        $err = Join-Path $PWD 'test-app.stderr.log'
                        $pidFile = Join-Path $PWD 'test-app.pid'

                        if (!(Test-Path $jar)) {
                            throw "Application JAR not found: $jar"
                        }

                        # Remove only a listener on the test port.
                        $listener = Get-NetTCPConnection `
                            -LocalPort $port -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            $existing = Get-CimInstance Win32_Process `
                                -Filter "ProcessId = $($listener.OwningProcess)"

                            if ($existing.CommandLine -notmatch
    'asset-allocation-portal.*[.]jar|asset-allocation-portal-0[.]0[.]1-SNAPSHOT[.]jar') {
                                throw "Port $port is occupied by an unrelated process (PID $($listener.OwningProcess))."
                            }

                            Stop-Process -Id $listener.OwningProcess -Force
                            Start-Sleep -Seconds 2
                        }

                        Remove-Item $out, $err, $pidFile `
                            -Force -ErrorAction SilentlyContinue

                        $p = Start-Process -FilePath 'java' `
                            -ArgumentList @(
                                '-Duser.timezone=UTC',
                                '-jar',
                                "`"$jar`"",
                                '--server.port=8082'
                            ) `
                            -PassThru `
                            -RedirectStandardOutput $out `
                            -RedirectStandardError $err

                        Set-Content -Path $pidFile -Value $p.Id

                        $ready = $false
                        for ($i = 0; $i -lt 45; $i++) {
                            Start-Sleep -Seconds 2

                            if ($p.HasExited) {
                                break
                            }

                            try {
                                $response = Invoke-WebRequest `
                                    -Uri 'http://127.0.0.1:8082/' `
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
                            throw 'Test application failed to become ready on port 8082.'
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
                    $pidFile = Join-Path $PWD 'test-app.pid'

                    if (Test-Path $pidFile) {
                        $appPid = [int](Get-Content $pidFile -Raw).Trim()
                        $p = Get-Process -Id $appPid -ErrorAction SilentlyContinue

                        if ($p) {
                            Stop-Process -Id $appPid -Force
                            Write-Host "Stopped test application PID $appPid."
                        }

                        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
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