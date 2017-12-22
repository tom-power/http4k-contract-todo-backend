package org.http4k.todo

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.format.Jackson.auto
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object TodoBackendAppSpec : Spek({

    beforeGroup {
        server.start()
    }

    afterGroup {
        server.stop()
    }

    describe("the pre-requisites") {
        it("the api root responds to a GET (i e the server is up and accessible, CORS headers are set up)") {
            getRoot() shouldMatch hasStatus(Status.OK)
        }

        it("the api root responds to a POST with the todo which was posted to it") {
            post(A_TODO) shouldMatch hasStatus(Status.OK)
                .and(hasBody(contains(A_TODO.toRegex())))
        }

        it("the api root responds successfully to a DELETE") {
            deleteRoot() shouldMatch hasStatus(Status.OK)
        }

        it("after a DELETE the api root responds to a GET with a JSON representation of an empty array") {
            deleteRoot()
            getRoot() shouldMatch hasStatus(Status.OK)
                .and(hasBody(todoListBody, isEmpty))
        }
    }

    describe("storing new todos by posting to the root url") {
        beforeEachTest {
            deleteRoot()
        }

        it("adds a new todo to the list of todos at the root url") {
            with(post(WALK_THE_DOG)) {
                assertThat(getRoot().todoList().size, equalTo(1))
                with(todo()) {
                    assertThat(title, equalTo(WALK_THE_DOG))
                }
            }
        }

        it("sets up a new todo as initially not completed") {
            with(post(BLAH).todo()) {
                assertThat(completed, equalTo(false))
            }
        }

        it("each new todo has a url") {
            with(post(BLAH).todo()) {
                assertThat(url.toString(), !isNullOrBlank)
            }
        }

        it("each new todo has a url, which returns a todo") {
            with(post(BLAH).todo()) {
                assertThat(get(url!!).todo().title, equalTo(BLAH))
            }
        }
    }

    describe("working with an existing todo") {
        beforeEachTest {
            deleteRoot()
        }

        it("can navigate from a list of todos to an individual todo via urls") {
            post(TODO_THE_FIRST)
            post(TODO_THE_SECOND)
            getRoot().run {
                assertThat(status, equalTo(Status.OK))
                with(todoArray()) {
                    assertThat(size, equalTo(2))
                    assertThat(get(first().url!!).todo().title, equalTo(TODO_THE_FIRST))
                }
            }
        }

        it("can change the todo's title by PATCHing to the todo's url") {
            with(post(INITIAL_TITLE).todo()) {
                assertThat(title, equalTo(INITIAL_TITLE))
                with(patch(TodoEntry(title = BATHE_THE_CAT), url).todo()) {
                    assertThat(title, equalTo(BATHE_THE_CAT))
                }
            }
        }

        it("can change the todo's completedness by PATCHing to the todo's url") {
            with(post(INITIAL_TITLE).todo()) {
                assertThat(completed, equalTo(false))
                with(patch(TodoEntry(completed = true), url).todo()) {
                    assertThat(completed, equalTo(true))
                }
            }
        }

        it("changes to a todo are persisted and show up when re-fetching the todo") {
            val verifyChangedTodo: (todoEntry: TodoEntry) -> Unit = {
                assertThat(it.title, equalTo(CHANGED_TITLE))
                assertThat(it.completed, equalTo(false))
            }

            with(post(INITIAL_TITLE).todo()) {
                with(patch(TodoEntry(title = CHANGED_TITLE, completed = false), url)) {
                    verifyChangedTodo(todo())
                    verifyChangedTodo(get(url!!).todo())
                }
            }
        }

        it("can delete a todo making a DELETE request to the todo's url") {
            with(post(INITIAL_TITLE).todo()) {
                delete(url!!)
            }
            assertThat(getRoot().todoList(), isEmpty)
        }
    }

    describe("tracking todo order") {
        it("can create a todo with an order field") {
            with(post(TodoEntry(title = BLAH, order = FIVE_TWO_THREE)).todo()) {
                assertThat(order, equalTo(FIVE_TWO_THREE))
            }
        }

        it("can PATCH a todo to change its order") {
            with(post(TodoEntry(title = BLAH, order = FIVE_TWO_THREE)).todo()) {
                with(patch(TodoEntry(order = NINE_FIVE), url).todo()) {
                    assertThat(order, equalTo(NINE_FIVE))
                }
            }
        }

        it("remembers changes to a todo's order") {
            val verifyChangedTodo: (todoEntry: TodoEntry) -> Unit = {
                assertThat(it.order, equalTo(NINE_FIVE))
            }

            with(post(TodoEntry(title = BLAH, order = FIVE_TWO_THREE)).todo()) {
                with(patch(TodoEntry(order = NINE_FIVE), url)) {
                    verifyChangedTodo(todo())
                    verifyChangedTodo(get(url!!).todo())
                }
            }
        }
    }

})

private val port = 5000
private val baseUrl = "http://localhost:$port"
private val todos = TodoDatabase(baseUrl)
private val todoApp = TodoApp(todos)
private val server = TodoServer(port, todoApp)
private val client = OkHttp()

private fun getRoot() = get(baseUrl)

private fun get(url: String) = request(Method.GET, url)

private fun post(title: String) = post(TodoEntry(title = title))

private fun post(todoEntry: TodoEntry) = inject(todoEntry, Method.POST, baseUrl)

private fun patch(todoEntry: TodoEntry, url: String?) = inject(todoEntry, Method.PATCH, url!!)

private fun inject(todoEntry: TodoEntry, method: Method, url: String): Response {
    return client(todoBody.inject(todoEntry, Request(method, url)))
}

private fun deleteRoot() = delete(baseUrl)

private fun delete(url: String) = request(Method.DELETE, url)

private fun request(method: Method, url: String) = client(Request(method, url))

private fun Response.todo() = todoBody.extract(this)

private fun Response.todoList() = todoListBody.extract(this)

private fun Response.todoArray() = Body.auto<Array<TodoEntry>>().toLens().extract(this)

private const val A_TODO = "a todo"
private const val WALK_THE_DOG = "walk the dog"
private const val BLAH = "blah"
private const val TODO_THE_FIRST = "todo the first"
private const val TODO_THE_SECOND = "todo the second"
private const val INITIAL_TITLE = "initial title"
private const val BATHE_THE_CAT = "bathe the cat"
private const val CHANGED_TITLE = "changed title"
private const val FIVE_TWO_THREE = 523
private const val NINE_FIVE = 95