import os
os.chdir(r"c:\Users\xjh37\Desktop\MSF\msf-friends-multi")
lines = ["pluginManagement {","    repositories {","        gradlePluginPortal()","        mavenCentral()","        maven { url = uri(\"https://maven.fabricmc.net/\") }","        maven { url = uri(\"https://maven.neoforged.net/releases/\") }","        maven { url = uri(\"https://maven.minecraftforge.net/\") }","        maven { url = uri(\"https://repo.spongepowered.org/maven/\") }","    }","}","dependencyResolutionManagement {","    repositories {","        mavenCentral()","        maven { url = uri(\"https://maven.fabricmc.net/\") }","        maven { url = uri(\"https://libraries.minecraft.net/\") }","        maven { url = uri(\"https://maven.minecraftforge.net/\") }","    }","}","rootProject.name = \"MSF\"","include(\":common\")","include(\":versions:1.17.1:common\")","include(\":versions:1.17.1:forge\")",""]
with open("settings.gradle.kts","w",encoding="utf-8",newline="\n") as f:
    for l in lines: f.write(l+"\n")
print("OK",os.path.getsize("settings.gradle.kts"),"bytes")

