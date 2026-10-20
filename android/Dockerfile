# Reproducible build environment for the Assistant Android app.
# Build: docker build -t assistant-android-build .
# Run (with a persistent Gradle cache so repeat builds don't redownload the Gradle
# distribution/dependencies every time, and a persistent debug keystore so `adb install`
# doesn't hit INSTALL_FAILED_UPDATE_INCOMPATIBLE on every rebuild -- first run is still
# slow, later ones are fast):
#   docker run --rm -v "<path-to-android-dir>:/workspace" -v assistant-gradle-cache:/root/.gradle \
#     -v assistant-android-home:/root/.android -w /workspace assistant-android-build ./gradlew assembleDebug
FROM eclipse-temurin:17-jdk-jammy

ENV ASSISTANT_DOCKER_BUILD=true
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -sSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

# build-tools 34.0.0 is included alongside 35.0.0 because AGP 8.5.2 (this project's version)
# still resolves 34.0.0 for some tasks regardless of compileSdk 35 -- without it baked in here,
# every container re-downloads it on first use.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;34.0.0"

WORKDIR /workspace
