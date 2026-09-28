fun main(args: Array<String>) {
    println("Hello ${getWorld()}")
    for ((index, arg) in args.withIndex()) {
        println("ARG${index}: <$arg>")
    }
}
