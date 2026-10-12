pipeline {
    agent any

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9.16'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Spring Boot App') {
            steps {
                bat 'mvn -B clean package'
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
                powershell '''
                    $ErrorActionPreference = 'Stop'

                    $jar = Join-Path $env:WORKSPACE 'target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar'

                    if (!(Test-Path $jar)) {
                        throw "Application JAR not found: $jar"
                    }

                    if (Test-Path 'test-app.pid') {
                        Remove-Item 'test-app.pid' -Force
                    }

                    Write-Host 'Starting test application on port 8082...'

                    $process = Start-Process `
                        -FilePath 'java' `
                        -ArgumentList @(
                            '-Duser.timezone=UTC',
'-jar',
"`"$jar`"",
'--server.port=8082',
'--spring.datasource.url=jdbc:postgresql://localhost:5432/assetdb'
                        ) `
                        -WorkingDirectory $env:WORKSPACE `
                        -RedirectStandardOutput (Join-Path $env:WORKSPACE 'test-app.log') `
                        -RedirectStandardError (Join-Path $env:WORKSPACE 'test-app-error.log') `
                        -PassThru

                    Set-Content -Path 'test-app.pid' -Value $process.Id

                    $ready = $false

                    for ($i = 0; $i -lt 45; $i++) {
                        Start-Sleep -Seconds 2

                        $process.Refresh()

                        if ($process.HasExited) {
                            Get-Content 'test-app.log' -ErrorAction SilentlyContinue
                            Get-Content 'test-app-error.log' -ErrorAction SilentlyContinue
                            throw 'Test application exited before becoming ready.'
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
                        } catch {
                            # Application may still be starting.
                        }
                    }

                    if (!$ready) {
                        Get-Content 'test-app.log' -ErrorAction SilentlyContinue
                        Get-Content 'test-app-error.log' -ErrorAction SilentlyContinue
                        throw 'Test application did not become ready on port 8082.'
                    }

                    Write-Host 'Test application is ready.'
                '''
            }
        }

        stage('Run Selenium Tests') {
            steps {
                dir('selenium-tests') {
                    bat 'mvn -B test "-Dapp.url=http://127.0.0.1:8082/"'
                }
            }
        }

        stage('Stop Test Application') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'

                    if (Test-Path 'test-app.pid') {
                        $pidValue = Get-Content 'test-app.pid' -ErrorAction SilentlyContinue

                        if ($pidValue) {
                            $testProcess = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue

                            if ($testProcess) {
                                Stop-Process -Id ([int]$pidValue) -Force
                                Write-Host 'Test application stopped.'
                            }
                        }

                        Remove-Item 'test-app.pid' -Force -ErrorAction SilentlyContinue
                    }
                '''
            }
        }

        stage('Verify Docker Access') {
            steps {
                bat '"C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" version'
            }
        }

        stage('Build Docker Image') {
            steps {
                bat '"C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" build -t asset-allocation-portal:%BUILD_NUMBER% .'
            }
        }

        stage('Deploy Docker Container') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'

                    $docker = 'C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
                    $image = "asset-allocation-portal:$env:BUILD_NUMBER"

                    Write-Host 'Verifying the Docker image...'

                    & $docker image inspect $image
                    if ($LASTEXITCODE -ne 0) {
                        throw "Docker image not found: $image"
                    }

                    Write-Host 'Checking for the previous application container...'

                    $existing = & $docker ps -aq --filter 'name=^/asset-portal$'

                    if ($LASTEXITCODE -ne 0) {
                        throw 'Could not check existing Docker containers.'
                    }

                    if ($existing) {
                        Write-Host 'Removing the previous application container...'

                        & $docker rm -f asset-portal

                        if ($LASTEXITCODE -ne 0) {
                            throw 'Could not remove the previous application container.'
                        }
                    }

                    Write-Host 'Starting the new application container...'

                    $runArgs = @(
                        'run',
                        '-d',
                        '--name', 'asset-portal',
                        '--restart', 'unless-stopped',
                        '-p', '8081:8081',
                        '-e', 'SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/assetdb',
                        $image
                    )

                    & $docker @runArgs

                    if ($LASTEXITCODE -ne 0) {
                        throw 'Docker could not start the application container.'
                    }

                    Write-Host 'Checking application health...'

                    $ready = $false

                    for ($i = 0; $i -lt 30; $i++) {
                        Start-Sleep -Seconds 2

                        try {
                            $response = Invoke-WebRequest `
                                -Uri 'http://127.0.0.1:8081/' `
                                -TimeoutSec 3 `
                                -UseBasicParsing

                            if ($response.StatusCode -eq 200) {
                                $ready = $true
                                break
                            }
                        } catch {
                            # Application may still be starting.
                        }
                    }

                    if (!$ready) {
                        & $docker logs --tail 60 asset-portal
                        throw 'Deployment failed health verification.'
                    }

                    Write-Host 'Deployment successful.'

                    & $docker ps --filter 'name=asset-portal'

                    if ($LASTEXITCODE -ne 0) {
                        throw 'Could not verify the deployed container.'
                    }
                '''
            }
        }
    }

    post {
        always {
            powershell '''
                if (Test-Path 'test-app.pid') {
                    $pidValue = Get-Content 'test-app.pid' -ErrorAction SilentlyContinue

                    if ($pidValue) {
                        $testProcess = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue

                        if ($testProcess) {
                            Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
                            Write-Host 'Cleaned up the temporary test application.'
                        }
                    }

                    Remove-Item 'test-app.pid' -Force -ErrorAction SilentlyContinue
                }
            '''
        }

        success {
            echo 'Pipeline completed successfully.'
            echo 'Build, Selenium tests, Docker image creation, and deployment passed.'
        }

        failure {
            echo 'Pipeline failed. Check the first failed stage in Console Output.'
        }

        aborted {
            echo 'Pipeline was aborted.'
        }
    }
}