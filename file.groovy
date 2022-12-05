String relativeBuildContainer = ""
String DOCKER_REGISTRY='sv2lxcad03.corp.equinix.com:5005'
String MAVEN_OPTS = 'MAVEN_OPTS=-Xmx1024M -Xms256M -Xmn256M'

stage('Check build container...') {
    podTemplate(yaml: """
  containers:
    - name: optimus-prime-build-bc
      image: sv2lxcad03.corp.equinix.com:5005/cad/optimus-prime-build-bc:d4f4f9571cdbd2ec0800b9bc396b5ff8ef607f55
""") {
        node(POD_LABEL) {
            container('optimus-prime-build-bc') {
                checkout()

                stage("generate tmp pom.xml") {
                    sh("cat pom.xml | sed -e 's/ xmlns.*=\".*\"//g' | xmlstarlet ed -P -d \"/project/dependencies/dependency[artifactId='Bumblebee']\" > pom1.xml")
                    sh("cat pom1.xml | sed -e 's/ xmlns.*=\".*\"//g' | xmlstarlet ed -P -d \"/project/dependencies/dependency[artifactId='gitlab-plugin']\" > pom.xml")
                    sh("rm -f pom1.xml")
                    sh("touch -t 200001010001 pom.xml")
                }

                relativeBuildContainer = getRelativeBuildContainerTag(DOCKER_REGISTRY, ['pom.xml', 'relative.dockerfile'])

                stage("Conditionally costruct the build container") {
                    if(withDockerRegistry.containerExists(relativeBuildContainer)) return

                    withDockerRegistry(url: DOCKER_REGISTRY) {
                        withEnv([MAVEN_OPTS]) {
                            sh('mvn dependency:go-offline')
                            sh('mv ~/.m2/repository ./repository')
                        }
                        cre.dockerBuild(dockerImageTag: relativeBuildContainer, dockerfilePath: 'relative.dockerfile', dockerBuildContext: './')
                    }
                }
            }
        }
    }
}

stage('build') {
    podTemplate(yaml: """
  containers:
    - name: relative-build-container
      image: ${relativeBuildContainer}
""") {
        node(POD_LABEL) {
            container('relative-build-container') {
                checkout(scm)

                stage('Running maven') {
                    withMavenRepository.maven {
                        withEnv([MAVEN_OPTS]) {
                            sh("mvn install ${commonOptions} -U")
                            sh("mvn hpi:custom-war ${commonOptions}")
                            sh("mvn hpi:hpi ${commonOptions}")
                        }
                    }
                }
                String appDockerTag = getImageTag()
                exceptForMergeRequest {
                    stage('docker') {
                        withDockerRegistry.withLegacyNexus {
                            sh("""
docker build --no-cache --rm -t ${appDockerTag} .
docker push ${appDockerTag}
""")
                        }
                    }
                    stage('deploy') {
                        if ("master".equals(env.BRANCH_NAME)) {
                            build job: 'deploy', parameters: [
                                    string(name: 'GITLAB_TRIGGER_DATA', value: env.GITLAB_TRIGGER_DATA),
                                    string(name: 'MASTER_IMAGE', value: appDockerTag)
                            ], wait: true
                        } else if ("development".equals(env.BRANCH_NAME)) {
                            build job: 'deploy', parameters: [
                                    string(name: 'GITLAB_TRIGGER_DATA', value: env.GITLAB_TRIGGER_DATA),
                                    string(name: 'DEVELOPMENT_IMAGE', value: appDockerTag)
                            ], wait: true
                        } else {
                            echo """Please test on your dev cluster
Your Jenkins Master docker is
${appDockerTag}
"""
                        }
                    }
                }
            }
        }
    }
}

def getImageTag() {
    String date = new Date().format("yyyyMMdd", TimeZone.getTimeZone('UTC'))
    String tag = "${env.GIT_COMMIT}-${date}"

    def gitTag = "dev-${tag}"

    if ("master".equals(env.BRANCH_NAME)) {
        gitTag = "master-${tag}"
    }

    return gitTag
}

def getRelativeBuildContainerTag(String dockerRegistry, List files) {
    List controlSums = files.collect { file ->
        return sha1(file)
    }

    String sumOfControlSums = hash.md5(controlSums.join("\n"))

    return "${dockerRegistry}/cad/optimus-prime-conditional-bc:${sumOfControlSums}"
}