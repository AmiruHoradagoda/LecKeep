pipeline {
    agent { label 'linux && docker' }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
    }

    tools {
        nodejs 'node-22.12.0'
    }

    parameters {
        string(name: 'VITE_API_URL', defaultValue: '/api/v1', description: 'Public API URL embedded in the frontend build; never put secrets here.')
    }

    environment {
        DOCKER_IMAGE_BACKEND = 'amiru1234/leckeep-backend:latest'
        DOCKER_IMAGE_FRONTEND = 'amiru1234/leckeep-frontend:latest'
        DOCKER_CREDENTIALS_ID = 'dockerhub-credentials'
        VITE_API_URL = "${params.VITE_API_URL}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend Tests') {
            steps {
                dir('leckeep_backend') {
                    // Invoke through sh because the checked-in wrapper is not executable.
                    sh 'sh ./mvnw -B clean test'
                }
            }
            post {
                always {
                    junit 'leckeep_backend/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Backend Build') {
            steps {
                dir('leckeep_backend') {
                    // Tests already passed in the preceding stage.
                    sh 'sh ./mvnw -B package -DskipTests'
                }
            }
        }

        stage('Frontend Install') {
            steps {
                dir('leckeep_frontend') {
                    sh 'npm ci'
                }
            }
        }

        stage('Frontend Build') {
            steps {
                dir('leckeep_frontend') {
                    sh 'npm run build'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -eu
                    docker build --tag "$DOCKER_IMAGE_BACKEND" leckeep_backend
                    docker build --build-arg VITE_API_URL="$VITE_API_URL" --tag "$DOCKER_IMAGE_FRONTEND" leckeep_frontend
                '''
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    def dockerConfigDir = "${pwd(tmp: true)}/docker-config"
                    withEnv(["DOCKER_CONFIG=${dockerConfigDir}"]) {
                        try {
                            withCredentials([usernamePassword(credentialsId: env.DOCKER_CREDENTIALS_ID,
                                    usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                                // Shell expansion keeps credentials out of Groovy strings and tracing.
                                sh '''
                                    set +x
                                    set -eu
                                    mkdir -p "$DOCKER_CONFIG"
                                    printf '%s' "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
                                    docker push "$DOCKER_IMAGE_BACKEND"
                                    docker push "$DOCKER_IMAGE_FRONTEND"
                                '''
                            }
                        } finally {
                            try {
                                sh 'docker logout'
                            } finally {
                                dir(dockerConfigDir) {
                                    deleteDir()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
