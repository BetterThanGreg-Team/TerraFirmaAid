{ pkgs, lib, ... }:

{
  packages = [
    pkgs.findutils  # provides xargs (needed by gradlew)
    pkgs.gnused     # sed
    pkgs.gnutar
    pkgs.gzip
    pkgs.curl
  ];

  languages.java = {
    enable = true;
    jdk.package = pkgs.zulu21;
  };

  env = {
    LD_LIBRARY_PATH = with pkgs; lib.makeLibraryPath [
      libGL
      glfw
      openal
      flite
      libpulseaudio
      udev
      libxcursor
      libXxf86vm
      libxrandr
      libxext
      libx11
    ];
  };
}
