@Library('jenkins-library@1.1.0') _
buildJavaProject(
        shouldRunIntegrationTests: true,
        integrationTestsEnvVars: ["BROKER_PRIVATE_KEY"],
        shouldPublishJars: false,
        shouldPublishDockerImages: true,
        dockerfileDir: './docker',
        //dockerfileFilename: "Dockerfile-local",
        buildContext: '.',
        //dockerImageRepositoryName: '',
        preProductionVisibility: 'docker.io',
        productionVisibility: 'docker.io')