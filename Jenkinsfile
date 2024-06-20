pipeline {

    agent {
        kubernetes {
            defaultContainer 'maven'
            yamlFile 'jenkins/agent.yaml'
        }
    }

    environment {
        GPG_SECRET     = credentials('presto-release-gpg-secret')
        GPG_TRUST      = credentials("presto-release-gpg-trust")
        GPG_PASSPHRASE = credentials("presto-release-gpg-passphrase")

        GITHUB_OSS_TOKEN_ID = 'github-personal-token-wanglinsong'

        SONATYPE_NEXUS_CREDS    = credentials('presto-sonatype-nexus-creds')
        SONATYPE_NEXUS_PASSWORD = "$SONATYPE_NEXUS_CREDS_PSW"
        SONATYPE_NEXUS_USERNAME = "$SONATYPE_NEXUS_CREDS_USR"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '100'))
        disableConcurrentBuilds()
        disableResume()
        overrideIndexTriggers(false)
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    parameters {
        booleanParam(name: 'PERFORM_MAVEN_RELEASE',
                     defaultValue: false,
                     description: 'Release and update development version when running in the master')
    }

    stages {
        stage('Setup') {
            steps {
                sh '''
                    apt update && apt install -y bash build-essential git gpg python3 python3-venv
                    git config --global --add safe.directory ${WORKSPACE}
                '''
            }
        }

        stage ('Debug GPG') {
            steps {
                sh '''#!/bin/bash -ex
                    export GPG_TTY=$TTY
                    gpg --batch --import ${GPG_SECRET}
                    echo ${GPG_TRUST} | gpg --import-ownertrust -
                    gpg --list-secret-keys
                    printenv | sort

                    mvn release:prepare -B \
                        -Dgpg.passphrase=${GPG_PASSPHRASE} \
                        -DautoVersionSubmodules=true \
                        -DgenerateBackupPoms=false \
                        -Poss-release
                '''
            }
        }
    }
}
