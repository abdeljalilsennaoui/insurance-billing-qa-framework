// Declarative Jenkins pipeline for the insurance billing QA framework.
//
// ---------------------------------------------------------------------------------------------------
// STATUS: this pipeline is NOT executed for this repository.
//
// GitHub Actions (.github/workflows/ci.yml) is the pipeline that actually runs, on every push and pull
// request, and its results are what gate merges. This Jenkinsfile is a reference implementation of the
// same stages for a Jenkins controller, because Jenkins remains the dominant CI server in enterprise QA
// environments and a QA engineer is expected to be able to read and write one.
//
// It has been written against the real project layout and the real Maven commands, but it has never been
// run: no Jenkins controller was available. Treating it as verified would be dishonest, so it is labelled
// here, in README.md and in the test strategy. Anyone adopting it should expect to adjust the agent
// definition, the JDK tool name and the plugin availability for their own controller.
// ---------------------------------------------------------------------------------------------------

pipeline {
    agent any

    tools {
        // Configure a JDK of this name under Manage Jenkins > Tools. Pinned to 25 to match
        // maven.compiler.release, so the pipeline cannot pass on a JDK the project does not target.
        jdk 'jdk-25'
        maven 'maven-3.9'
    }

    options {
        timestamps()
        // Generous enough for the browser suites on a slow agent, bounded so a hung Chrome process
        // cannot occupy an executor indefinitely.
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    environment {
        APP_BASE_URL = 'http://localhost:8080'
    }

    stages {
        stage('Build') {
            steps {
                // -DskipITs excludes the suites that need a running application. They run in the stages
                // below, once it is started. -DskipTests alone would not be enough: it does not stop
                // maven-failsafe-plugin 3.6.0 executing the integration suites.
                sh 'mvn -B clean install -DskipITs'
            }
        }

        stage('Application tests') {
            steps {
                sh 'mvn -B test -pl billing-app'
            }
            post {
                always {
                    junit testResults: 'billing-app/target/surefire-reports/*.xml',
                          allowEmptyResults: false
                }
            }
        }

        stage('Start application') {
            steps {
                // The same script the developers and GitHub Actions use, so the three cannot diverge.
                // It polls the health endpoint rather than sleeping, and refuses to start if another
                // process already holds the port.
                sh 'scripts/start-app.sh'
            }
        }

        stage('API suite') {
            steps {
                sh 'mvn -B verify -pl qa-api-tests'
            }
            post {
                always {
                    junit testResults: 'qa-api-tests/target/failsafe-reports/*.xml',
                          allowEmptyResults: false
                }
            }
        }

        stage('UI suite') {
            steps {
                // Headless by default; no DISPLAY or Xvfb needed. Selenium Manager resolves a
                // chromedriver matching the agent's Chrome, so there is no driver to install or pin.
                sh 'mvn -B verify -pl qa-ui-tests'
            }
            post {
                always {
                    junit testResults: 'qa-ui-tests/target/failsafe-reports/*.xml',
                          allowEmptyResults: false
                    archiveArtifacts artifacts: 'qa-ui-tests/target/screenshots/*.png',
                                     allowEmptyArchive: true
                }
            }
        }

        stage('BDD scenarios') {
            steps {
                sh 'mvn -B verify -pl qa-bdd-tests'
            }
            post {
                always {
                    junit testResults: 'qa-bdd-tests/target/failsafe-reports/*.xml',
                          allowEmptyResults: false
                    archiveArtifacts artifacts: 'qa-bdd-tests/target/cucumber-reports/**',
                                     allowEmptyArchive: true
                    archiveArtifacts artifacts: 'qa-bdd-tests/target/screenshots/*.png',
                                     allowEmptyArchive: true
                }
            }
        }

        stage('Cypress smoke') {
            steps {
                dir('cypress') {
                    // npm ci, not npm install: installs exactly the committed lockfile so a transitive
                    // release cannot change what runs. cypress install is explicit because npm can
                    // decline to run the postinstall hook under its allow-scripts policy.
                    sh 'npm ci'
                    sh 'npx cypress install'
                    sh 'npx cypress run'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'cypress/cypress/screenshots/**',
                                     allowEmptyArchive: true
                }
            }
        }
    }

    post {
        // The application is stopped whatever the outcome. Without this, a failing suite would leave a
        // process holding port 8080 and every subsequent build on this agent would fail at
        // 'Start application' for a reason unrelated to the change that broke it.
        always {
            sh 'scripts/stop-app.sh || true'
            archiveArtifacts artifacts: 'billing-app/target/app.log', allowEmptyArchive: true
        }
        failure {
            echo 'Build failed. Check the archived application log and the failure screenshots.'
        }
    }
}
