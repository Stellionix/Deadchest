allprojects {
    group = "me.crylonz.deadchest"
    version = "4.30.0"
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
