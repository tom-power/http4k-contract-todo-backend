package org.http4k.todo

import org.http4k.contract.Root
import org.http4k.contract.Route
import org.http4k.contract.RouteModule
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.CorsPolicy.Companion.UnsafeGlobalPermissive
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters.Cors
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0] else "5000"
    val baseUrl = if (args.size > 1) args[1] else "http://localhost:$port"
    val todos = TodoDatabase(baseUrl)

    val globolFilters = DebuggingFilters.PrintRequestAndResponse().then(Cors(UnsafeGlobalPermissive))

    val todoBody = Body.auto<TodoEntry>().toLens()
    val todoListBody = Body.auto<List<TodoEntry>>().toLens()

    fun lookup(id: String): HttpHandler = { todos.find(id)?.let { Response(OK).with(todoBody of it) } ?: Response(NOT_FOUND) }
    fun patch(id: String): HttpHandler = { Response(OK).with(todoBody of todos.save(id, todoBody(it))) }
    fun delete(id: String): HttpHandler = { todos.delete(id)?.let { Response(OK).with(todoBody of it) } ?: Response(NOT_FOUND) }
    fun list(): HttpHandler = { Response(OK).with(todoListBody of todos.all()) }
    fun clear(): HttpHandler = { Response(OK).with(todoListBody of todos.clear()) }
    fun save(): HttpHandler = { Response(OK).with(todoBody of todos.save(null, todoBody(it))) }

    globolFilters.then(
        RouteModule(Root)
            .withRoute(Route().at(GET) / Path.of("id") bind ::lookup)
            .withRoute(Route().at(PATCH) / Path.of("id") bind ::patch)
            .withRoute(Route().at(DELETE) / Path.of("id") bind ::delete)
            .withRoute(Route().at(GET) bind list())
            .withRoute(Route().at(POST) bind save())
            .withRoute(Route().at(DELETE) bind clear())
            .toHttpHandler())
        .asServer(Jetty(port.toInt())).start().block()
}

data class TodoEntry(val id: String? = null, val url: String? = null, val title: String? = null, val order: Int? = 0, val completed: Boolean? = false)
