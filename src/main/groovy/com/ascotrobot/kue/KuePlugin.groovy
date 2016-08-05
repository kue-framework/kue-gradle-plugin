package com.ascotrobot.kue

import org.gradle.api.invocation.Gradle
import org.gradle.api.Project
import org.gradle.api.Plugin

class KuePlugin implements Plugin<Project> {

    void apply(Project project) {

        project.extensions.create("kue", KuePluginExtension)

        project.task('run') << {
            def classpath = project.sourceSets.main.runtimeClasspath.getFiles().collect {it.getAbsolutePath()}.join(':')
            Thread.start {
                def proc = "java -cp $classpath $project.kue.mainClass".execute()
                Gradle.addShutdownHook {
                    proc.destroy()
                }
                if (proc in UNIXProcess) {
                    def runningPidFile = new File("./RUNNING_PID")
                    runningPidFile.write(proc.pid.toString())
                }
                Thread.start { printStream(proc.inputStream) }
                Thread.start { printStream(proc.errorStream) }
            }
        }

        project.task('kill') << {
            def runningPidFile = new File("./RUNNING_PID")
            if (runningPidFile.exists()) {
                def pid = runningPidFile.readLines()[0]
                "kill $pid".execute()
            }
        }

        project.tasks.run.dependsOn([':build', ":kill", ":flywayMigrate"])
        project.tasks.flywayMigrate.mustRunAfter project.tasks.kill

    }


    static void printStream(InputStream is) {
        def bytes = new byte[32]
        def read = 0
        while((read = is.read(bytes)) != -1) {
            print (new String(bytes, 0, read, "UTF-8"))
        }
    }
}

class KuePluginExtension {
    String mainClass = 'com.ascotrobot.kue.internal.Main'
}