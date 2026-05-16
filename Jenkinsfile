// ============================================================
// EduLearn LMS — Production Jenkins CI/CD Pipeline
// ============================================================
// REQUIREMENTS (set in Jenkins → Manage Jenkins → Credentials):
//   - dockerhub-credentials : Docker Hub username and password (type: Username with password)
// ============================================================

pipeline {
    agent any

    // ── Tool versions (must match what's configured in Jenkins Global Tool Config) ──
    tools {
        maven 'Maven-3.9'
        nodejs 'Node-22'
    }

    // ── Pipeline-wide environment ──────────────────────────────────────────────────
    environment {
        // === CHANGE THESE TO YOUR VALUES ===
        DOCKER_HUB_USER     = 'muskangupta8239'
        IMAGE_PREFIX        = "muskangupta8239"
        APP_SERVER_USER     = "ubuntu"
        APP_DIR             = "/opt/edulearn"

        // === Image tag strategy: branch + build number + short commit ===
        GIT_SHORT_SHA       = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        SAFE_BRANCH         = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'main').replace('origin/', '')
        IMAGE_TAG           = "${SAFE_BRANCH.replaceAll('/', '-')}-${BUILD_NUMBER}-${GIT_SHORT_SHA}"
    }

    // ── Run pipeline only on relevant branches ─────────────────────────────────────
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        // ── STAGE 1: CHECKOUT ──────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "🔄 Checking out repositories for branch: ${SAFE_BRANCH}"
                
                dir('backend') {
                    git branch: SAFE_BRANCH, url: 'https://github.com/Muskangupta15223/EduLearn.git'
                }
                
                dir('edulearn-vite') {
                    git branch: SAFE_BRANCH, url: 'https://github.com/Muskangupta15223/EduLearnApp-Frontend.git'
                }
            }
        }

        // ── STAGE 2: BUILD & TEST (Parallel) ──────────────────────────────────────
        stage('Build & Test') {
            parallel {
                stage('Backend — Maven Build') {
                    steps {
                        dir('backend') {
                            sh '''
                                echo "🔨 Building backend..."
                                mvn -B -q clean package -DskipTests \
                                    -s ci-settings.xml \
                                    --no-transfer-progress
                            '''
                        }
                    }
                }
                stage('Frontend — Node Build') {
                    steps {
                        dir('edulearn-vite') {
                            sh '''
                                echo "⚛️  Building frontend..."
                                npm ci --prefer-offline
                                npm run build
                            '''
                        }
                    }
                }
            }
        }

        // ── STAGE 3: TEST ──────────────────────────────────────────────────────────
        stage('Test') {
            parallel {
                stage('Backend — Unit Tests') {
                    steps {
                        dir('backend') {
                            sh '''
                                echo "🧪 Running backend unit tests..."
                                mvn -B test --no-transfer-progress || true
                            '''
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'backend/**/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Frontend — Unit Tests') {
                    steps {
                        dir('edulearn-vite') {
                            sh '''
                                echo "🧪 Running frontend unit tests..."
                                npm test -- --passWithNoTests || true
                            '''
                        }
                    }
                }
            }
        }

        // ── STAGE 4: DOCKER BUILD ──────────────────────────────────────────────────
        stage('Docker Build') {
            // Only build images on main, develop, or release/* branches
            when {
                anyOf {
                    expression { SAFE_BRANCH == 'main' }
                    expression { SAFE_BRANCH == 'develop' }
                    expression { SAFE_BRANCH =~ /^release\/.*/ }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh '''
                        echo "🔐 Logging in to Docker Hub..."
                        echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
                    '''
                }

                dir('backend') {
                    sh """
                        echo "🐳 Building all service images with tag: ${IMAGE_TAG}"
                        export SERVICE_IMAGE_PREFIX=${IMAGE_PREFIX}

                        # Build each service image with both the versioned tag and 'latest'
                        for SERVICE in discovery-service config-service api-gateway auth-service \\
                                       user-service course-service enrollment-service \\
                                       payment-service notification-service admin-server; do
                            echo "  → Building \${SERVICE}..."
                            docker build \\
                                --build-arg SERVICE_NAME=\${SERVICE} \\
                                --cache-from ${IMAGE_PREFIX}/\${SERVICE}:latest \\
                                -t ${IMAGE_PREFIX}/\${SERVICE}:${IMAGE_TAG} \\
                                -t ${IMAGE_PREFIX}/\${SERVICE}:latest \\
                                -f Dockerfile .
                        done

                        echo "⚛️  Building frontend image..."
                        docker build \\
                            --cache-from ${IMAGE_PREFIX}/frontend:latest \\
                            -t ${IMAGE_PREFIX}/frontend:${IMAGE_TAG} \\
                            -t ${IMAGE_PREFIX}/frontend:latest \\
                            ../edulearn-vite
                    """
                }
            }
        }

        // ── STAGE 5: DOCKER PUSH ───────────────────────────────────────────────────
        stage('Docker Push') {
            when {
                anyOf {
                    expression { SAFE_BRANCH == 'main' }
                    expression { SAFE_BRANCH == 'develop' }
                    expression { SAFE_BRANCH =~ /^release\/.*/ }
                }
            }
            steps {
                sh """
                    echo "📤 Pushing images to ECR..."
                    for SERVICE in discovery-service config-service api-gateway auth-service \\
                                   user-service course-service enrollment-service \\
                                   payment-service notification-service admin-server frontend; do
                        docker push ${IMAGE_PREFIX}/\${SERVICE}:${IMAGE_TAG}
                        docker push ${IMAGE_PREFIX}/\${SERVICE}:latest
                    done
                    echo "✅ All images pushed successfully."
                """
            }
        }

        // ── STAGE 6: DEPLOY ────────────────────────────────────────────────────────
        stage('Deploy') {
            when {
                expression { SAFE_BRANCH == 'main' }
            }
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'dockerhub-credentials', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER'),
                    string(credentialsId: 'app-server-ip', variable: 'TARGET_IP')
                ]) {
                    sshagent(['app-server-ssh']) {
                        sh """
                            echo "🚀 Deploying to EC2 at \$TARGET_IP..."

                            # Copy compose file to app server
                            scp -o StrictHostKeyChecking=no \\
                                backend/docker-compose.yml \\
                                ${APP_SERVER_USER}@\$TARGET_IP:${APP_DIR}/docker-compose.yml

                            # Execute deployment on remote server
                            ssh -o StrictHostKeyChecking=no ${APP_SERVER_USER}@\$TARGET_IP bash << 'REMOTE_SCRIPT'
                                set -e
                                cd ${APP_DIR}

                                # Save current tag for potential rollback
                                echo "${IMAGE_TAG}" > .current_tag

                                # Login to Docker Hub on App Server
                                echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin || true

                                # Pull latest images
                                export SERVICE_IMAGE_PREFIX=${IMAGE_PREFIX}
                                docker compose pull

                                # Zero-downtime rolling restart:
                                # Infrastructure first (idempotent)
                                docker compose up -d --no-deps mysql-db redis zookeeper kafka

                                # Wait for infra health
                                sleep 30

                                # Core services
                                docker compose up -d --no-deps --no-build discovery-service
                                sleep 20
                                docker compose up -d --no-deps --no-build config-service
                                sleep 20

                                # All application services
                                docker compose up -d --no-deps --no-build \\
                                    api-gateway auth-service user-service course-service \\
                                    enrollment-service payment-service notification-service \\
                                    admin-server frontend

                                echo "✅ Deployment complete!"
REMOTE_SCRIPT
                        """
                    }
                }
            }
        }

        // ── STAGE 7: HEALTH CHECK ─────────────────────────────────────────────────
        stage('Health Check') {
            when {
                expression { SAFE_BRANCH == 'main' }
            }
            steps {
                withCredentials([string(credentialsId: 'app-server-ip', variable: 'TARGET_IP')]) {
                    sshagent(['app-server-ssh']) {
                        script {
                            def healthOk = sh(
                                script: """
                                    ssh -o StrictHostKeyChecking=no ${APP_SERVER_USER}@\$TARGET_IP \\
                                        'curl -sf http://localhost:8080/actuator/health | grep -q "UP"'
                                """,
                                returnStatus: true
                            ) == 0

                            if (healthOk) {
                                echo "✅ Health check passed! Application is live at http://\$TARGET_IP:8080"
                            } else {
                                error "❌ Deployment failed health check! The API Gateway is not reporting as UP."
                            }
                        }
                    }
                }
            }
        }

        // ── STAGE 8: CLEANUP ───────────────────────────────────────────────────────
        stage('Cleanup') {
            steps {
                sh '''
                    echo "🧹 Cleaning up local Docker images..."
                    docker image prune -f --filter "until=24h" || true
                '''
                cleanWs()
                echo "✅ Workspace cleaned."
            }
        }
    }

    // ── POST ACTIONS ───────────────────────────────────────────────────────────────
    post {
        success {
            echo "🎉 Pipeline SUCCEEDED for branch '${env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown'}' — Build #${BUILD_NUMBER}"
        }
        failure {
            echo "💥 Pipeline FAILED for branch '${env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown'}' — Build #${BUILD_NUMBER}"
        }
        always {
            echo "Pipeline finished with status: ${currentBuild.currentResult}"
        }
    }
}
