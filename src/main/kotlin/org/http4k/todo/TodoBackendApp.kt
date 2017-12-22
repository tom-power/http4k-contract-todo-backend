package org.http4k.todo

import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.CorsPolicy.Companion.UnsafeGlobalPermissive
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0] else "5000"
    val baseUrl = if (args.size > 1) args[1] else "http://localhost:$port"
    val todos = TodoDatabase(baseUrl)

    TodoServer(port.toInt(), TodoApp(todos)).start().block()
}

fun TodoApp(todos: TodoDatabase): HttpHandler {

    fun lookup(id: String): HttpHandler = { todos.find(id)?.let { Response(OK).with(todoBody of it) } ?: Response(NOT_FOUND) }
    fun patch(id: String): HttpHandler = { Response(OK).with(todoBody of todos.save(id, todoBody.extract(it))) }
    fun delete(id: String): HttpHandler = { todos.delete(id)?.let { Response(OK).with(todoBody of it) } ?: Response(NOT_FOUND) }
    fun list(): HttpHandler = { Response(OK).with(todoListBody of todos.all()) }
    fun clear(): HttpHandler = { Response(OK).with(todoListBody of todos.clear()) }
    fun save(): HttpHandler = { Response(OK).with(todoBody of todos.save(null, todoBody.extract(it))) }

    val globalFilters = DebuggingFilters.PrintRequestAndResponse().then(ServerFilters.Cors(UnsafeGlobalPermissive))

    return globalFilters.then(
        routes(
            contract(
                Path.of("id") bindContract GET to ::lookup,
                Path.of("id") bindContract PATCH to ::patch,
                Path.of("id") bindContract DELETE to ::delete,
                "/" bindContract GET to list(),
                "/" bindContract POST to save(),
                "/" bindContract DELETE to clear()
            )
        ))
}

val todoBody = Body.auto<TodoEntry>().toLens()
val todoListBody = Body.auto<List<TodoEntry>>().toLens()

fun TodoServer(portNum: Int, todoApp: HttpHandler) = todoApp.asServer(Jetty(portNum))

data class TodoEntry(val id: String? = null,
                     val url: String? = null,
                     val title: String? = null,
                     val order: Int? = 0,
                     val completed: Boolean? = false
)
