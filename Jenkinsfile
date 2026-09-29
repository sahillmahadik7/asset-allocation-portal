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
        powershell """
            \$appPort = '${params.APP_PORT}'
            \$pidFile = "deploy.pid"

            if (Test-Path \$pidFile) {
                \$oldPid = Get-Content \$pidFile

                if (\$oldPid) {
                    \$oldProcess = Get-Process -Id \$oldPid -ErrorAction SilentlyContinue

                    if (\$oldProcess) {
                        Stop-Process -Id \$oldPid -Force
                        Write-Host "Stopped previous application process: \$oldPid"
                    }
                }

                Remove-Item \$pidFile -Force
            }

            \$jar = Get-ChildItem "target\\\\*.jar" |
                   Where-Object { \$_.Name -notmatch "original" } |
                   Select-Object -First 1

            if (-not \$jar) {
                throw "JAR file not found."
            }

            Write-Host "Deploying: \$($jar.FullName)"
            Write-Host "Application port: \$appPort"

            \$process = Start-Process `
                -FilePath "cmd.exe" `
                -ArgumentList "/c", "start", """", "/b", "java", "-Duser.timezone=UTC", "-jar", "`"\$($jar.FullName)`"", "--server.port=\$appPort" `
                -WorkingDirectory \$env:WORKSPACE `
                -PassThru

            Start-Sleep -Seconds 3

            \$javaProcess = Get-Process -Name "java" -ErrorAction SilentlyContinue |
                            Where-Object { \$_.Id -ne \$PID } |
                            Sort-Object StartTime -Descending |
                            Select-Object -First 1

            if (-not \$javaProcess) {
                throw "Application process did not start."
            }

            Set-Content \$pidFile \$javaProcess.Id

            Write-Host "Application started with PID: \$($javaProcess.Id)"
            Write-Host "Deployment completed. Jenkins can continue."
        """
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