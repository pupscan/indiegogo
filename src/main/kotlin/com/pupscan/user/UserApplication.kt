package com.pupscan.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.client.RestTemplate
import java.io.File

@SpringBootApplication
class UserApplication : CommandLineRunner {
    val logger = LoggerFactory.getLogger(UserApplication::class.java)!!

    override fun run(vararg arguments: String) {
        if (arguments.isEmpty()) {
            logger.error("You need to set an Indiegogo project Id")
            return
        }
        val projectId = arguments[0].toLong()
        logger.info("Start fetching users for $projectId")
        val userName = mutableListOf<String>()
        val data = fetch(1)
        userName.addAll(data.response.map { it.by })
        val pagination = data.pagination
        (2..pagination.pages).forEach {
            val userSave = it * pagination.per_page
            logger.info("$userSave users saved")
            userName.addAll(fetch(it).response.map { it.by })
        }
        val finder = finderInComment(userName, projectId)
        File("./users from $projectId.csv").printWriter().use { out ->
            out.println("name;comment")
            userName.forEach {
                if (finder.containsKey(it)) {
                    out.println("\"$it\";\"${finder[it]}\"")
                } else {
                    out.println("\"$it\";\"\"")
                }

            }
        }
    }


    fun finderInComment(users: Collection<String>, projectId: Long): Map<String?, String> {
        val comments = mutableListOf<Comments>()
        val users = users.toSet()
        val data = fetchComments(1)
        comments.addAll(data.response)
        val pagination = data.pagination

        (2..pagination.pages).forEach {
            val commentsSave = it * pagination.per_page
            logger.info("$commentsSave comments saved")
            comments.addAll(fetchComments(it).response)
        }

        File("./comments from $projectId.csv").printWriter().use { out ->
            out.println("name;comment")
            comments.forEach { out.println("\"${it.account.name}\";\"${it.text.escapeln()}\"") }
        }

        return comments
                .filter { users.contains(it.account.name) }
                .filter { it.text.contains('@') }
                .map { it.account.name to it.text.escapeln() }
                .toMap()
    }

    fun String.escapeln() = this.replace("\n", "\\n").replace("\r", "\\n").replace("|", "").replace("\"", "")

    fun fetchComments(page: Int) = RestTemplate().getForObject("https://api.indiegogo.com/1" +
            ".1/campaigns/1648495/comments" +
            ".json?api_token=16e63457e7a24c06d39b40b52c0df273098cab82ccd3d4abaafd1a9c7a4edfe7&page=$page",
            ResponseComments::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class ResponseComments(val response: List<Comments>, val pagination: Pagination)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Comments(val account: Account, val text: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Account(val lastname: String?, val name: String?)

    fun fetch(page: Int) = RestTemplate().getForObject("https://api.indiegogo.com/1" +
            ".1/campaigns/1648495/contributions" +
            ".json?api_token=16e63457e7a24c06d39b40b52c0df273098cab82ccd3d4abaafd1a9c7a4edfe7&page=$page",
            Response::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Response(val response: List<UserName>, val pagination: Pagination)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class UserName(val by: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Pagination(val pages: Int, val per_page: Int)
}

fun main(args: Array<String>) {
    SpringApplication.run(UserApplication::class.java, *args)
}
