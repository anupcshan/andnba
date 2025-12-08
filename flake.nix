{
  description = "Android development environment with emulator support";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        buildToolsVersion = "34.0.0";
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          # Gradle plugin 8.2.0 requires build tools 34
          buildToolsVersions = [ buildToolsVersion "30.0.3" ];
          platformVersions = [ "34" "33" ];

          # Android SDK components
          includeEmulator = true;
          includeSources = false;
          includeSystemImages = true;

          # System images for emulator - x86_64 for best performance
          systemImageTypes = [ "google_apis_playstore" ];
          abiVersions = [ "x86_64" "arm64-v8a" ];

          # CMake and NDK (optional, uncomment if needed)
          # includeNDK = true;
          # ndkVersions = [ "25.1.8937393" ];
          # cmakeVersions = [ "3.22.1" ];

          # Extra packages
          extraLicenses = [
            "android-googletv-license"
            "android-sdk-arm-dbt-license"
            "android-sdk-preview-license"
            "google-gdk-license"
            "intel-android-extra-license"
            "intel-android-sysimage-license"
            "mips-android-sysimage-license"
          ];
        };

        androidSdk = androidComposition.androidsdk;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            # JDK 17 required for AGP 8.x
            pkgs.jdk17

            # Android SDK
            androidSdk

            # Gradle (project uses wrapper, but useful for IDE)
            pkgs.gradle

            # Useful tools
            pkgs.kotlin
            pkgs.ktlint
          ];

          # Environment variables
          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          JAVA_HOME = pkgs.jdk17.home;

          # For hardware acceleration (KVM)
          # User needs to be in 'kvm' group: sudo usermod -aG kvm $USER

          shellHook = ''
            echo "Android development environment loaded"
            echo ""
            echo "Android SDK: $ANDROID_SDK_ROOT"
            echo "Java Home: $JAVA_HOME"
            echo "Java version: $(java -version 2>&1 | head -1)"
            echo ""
            echo "Useful commands:"
            echo "  ./gradlew assembleDebug     - Build debug APK"
            echo "  ./gradlew installDebug      - Install to connected device/emulator"
            echo "  ./gradlew test              - Run unit tests"
            echo ""
            echo "Emulator commands:"
            echo "  emulator -list-avds         - List available AVDs"
            echo "  avdmanager list device      - List device definitions"
            echo "  avdmanager create avd \\     - Create an AVD"
            echo "    -n Pixel_6_API_34 \\"
            echo "    -k 'system-images;android-34;google_apis_playstore;x86_64' \\"
            echo "    -d pixel_6"
            echo "  emulator -avd Pixel_6_API_34 - Run emulator"
            echo ""
            echo "Note: For hardware acceleration, ensure your user is in the 'kvm' group:"
            echo "  sudo usermod -aG kvm $USER"
            echo "  (logout and login again for group change to take effect)"

            # Create local.properties if it doesn't exist
            if [ ! -f local.properties ]; then
              echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
              echo "Created local.properties with SDK path"
            fi
          '';
        };
      });
}
