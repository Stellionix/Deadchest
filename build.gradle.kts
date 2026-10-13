allprojects {
    group = "me.crylonz.deadchest"
    version = "4.31.1"
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
