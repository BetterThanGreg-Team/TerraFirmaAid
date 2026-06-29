{ pkgs, lib, ... }:

{
  packages = [];

  languages.java = {
    enable = true;
    jdk.package = pkgs.zulu21;
  };

  env.LD_LIBRARY_PATH = with pkgs; lib.makeLibraryPath [
    libGL
    glfw
    openal
    flite
    libpulseaudio
    udev
    libxcursor
    libxxf86vm
    libxrandr
    libxext
    libx11
  ];
}
