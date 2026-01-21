import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

// Allow dynamic build directory for multi-version builds
if (project.hasProperty("targetBuildDir")) {
    layout.buildDirectory.set(file(project.property("targetBuildDir") as String))
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
}

// Dynamic Version Logic
val mcVer = project.findProperty("mcVer") as? String ?: libs.versions.minecraft.get()
val yarnVer = project.findProperty("yarnVer") as? String ?: libs.versions.yarn.mappings.get()
val meteorVer = project.findProperty("meteorVer") as? String ?: libs.versions.meteor.get()

dependencies {
    // Dynamic dependencies
    minecraft("com.mojang:minecraft:$mcVer")
    mappings("net.fabricmc:yarn:$yarnVer:v2")
    
    modImplementation(libs.fabric.loader)
    modImplementation("meteordevelopment:meteor-client:$meteorVer")
}


tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to mcVer
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
    
// End of tasks block usage
}

// Dynamic build tasks logic moved to script scope
val targets = mapOf(
    "1.21.4" to "1.21.4+build.8",
    "1.21.5" to "1.21.5+build.1",
    "1.21.6" to "1.21.6+build.1",
    "1.21.7" to "1.21.7+build.8",
    "1.21.8" to "1.21.8+build.1",
    "1.21.10" to "1.21.10+build.3",
    "1.21.11" to "1.21.11+build.1"
)

// Helper to read property safely
val rawModVersion = project.properties["mod_version"] as? String ?: "1.0.0"

// Create tasks for each version
val buildTaskProviders = ArrayList<TaskProvider<Exec>>()

targets.forEach { (mc, yarn) ->
    val t = tasks.register<Exec>("build_v$mc") {
        group = "build versions"
        description = "Builds release for Minecraft $mc"
        
        val versionBuildDir = "build/versions/$mc"
        val meteor = "$mc-SNAPSHOT"
        val computedVersion = rawModVersion.replace("{mc_version}", mc)
        
        val gradleCmd = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        
        // Set executable and args
        executable = gradleCmd
        args(
            "build", 
            "-PmcVer=$mc", 
            "-PyarnVer=$yarn", 
            "-PmeteorVer=$meteor", 
            "-Pmod_version=$computedVersion", 
            "-PtargetBuildDir=$versionBuildDir"
        )
        
        doFirst {
            println("Building $computedVersion for MC $mc...")
        }
    }
    buildTaskProviders.add(t)
}

// Enforce sequential execution
for (i in 1 until buildTaskProviders.size) {
    buildTaskProviders[i].configure {
        mustRunAfter(buildTaskProviders[i - 1])
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the mod for all targeted Minecraft versions sequentially"
    
    dependsOn(buildTaskProviders)
    
    doLast {
        println("All builds completed. Check 'build/versions/' for artifacts.")
    }
}
