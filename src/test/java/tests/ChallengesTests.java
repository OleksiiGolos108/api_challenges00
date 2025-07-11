package tests;

import dto.challenge.ChallengesResponse;
import dto.challenge.Todos;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.jupiter.api.Assertions.*;
import static specs.ChallengesSpecs.defaultSpec;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

public class ChallengesTests extends TestBase {

    static String xChallengerHeader = "";

    @BeforeAll
    static void beforeAll() {
        Response response =
                given()

                        .when()
                        .post("/challenger")

                        .then()
                        .statusCode(201)
                        .extract().response();

        xChallengerHeader = response.header("X-Challenger");
    }

    @Test
    @Order(1)
    void xChallengerHeaderIsNotEmpty() {

        Assertions.assertNotNull(xChallengerHeader, "x-challenger header is null");
        Assertions.assertNotEquals(xChallengerHeader, "", "x-challenger header is empty");
        Assertions.assertTrue(xChallengerHeader.length() > 10, "x-challenger header lenghth is less than 10");

    }

    @Test
    @Order(2)
    void getChallengesAndCheckThatFirstTwoAreDone() {
        ExtractableResponse response =
                given()
                        .header("X-Challenger", xChallengerHeader)

                        .when()
                        .get("/challenges")

                        .then()
                        .statusCode(200)
                        .extract();

        ChallengesResponse challengesResponse = response.as(ChallengesResponse.class);


        assertEquals(challengesResponse.getChallenges().get(0).getName(), "POST /challenger (201)");
        Assertions.assertTrue(challengesResponse.getChallenges().get(0).isStatus());

        assertEquals(challengesResponse.getChallenges().get(1).getName(), "GET /challenges (200)");
        Assertions.assertTrue(challengesResponse.getChallenges().get(1).isStatus());

    }

    @Test
    @Order(3)
    void getTodos() {
        ExtractableResponse response =
                given()
                        .header("X-Challenger", xChallengerHeader)

                        .when()
                        .get("/todos")

                        .then()
                        .statusCode(200)
                        .extract();

        Todos todosResponse = response.as(Todos.class);

        assertFalse(todosResponse.getTodos().get(0).isDoneStatus());
        assertFalse(todosResponse.getTodos().get(1).isDoneStatus());
        Assertions.assertTrue(todosResponse.getTodos().size() > 5);

    }

    @Test
    @Order(4)
    void getTodo2() {
        ExtractableResponse<Response> response =
                given(defaultSpec)
                        .header("X-Challenger", xChallengerHeader)

                        .when()
                        .get("/todos/2")

                        .then()
                        .statusCode(200)
                        .body(matchesJsonSchemaInClasspath("schemas/todos_response.json"))
                        .extract();

        Todos todosResponse = response.as(Todos.class);

        assertEquals(2, todosResponse.getTodos().get(0).getId(), "Todo ID is not 2");
        assertEquals("file paperwork", todosResponse.getTodos().get(0).getTitle(), "Todo title is not as expected");
        assertEquals("", todosResponse.getTodos().get(0).getDescription(), "Todo description is not as expected");
        assertFalse(todosResponse.getTodos().get(0).isDoneStatus());
    }

    @Test
    @Order(5)
    void getTodo_IncorrectPath_ShouldReturn404() {
        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .get("/todo") // неправильный путь
                .then()
                .statusCode(404)
                .extract()
                .response();

        // Проверяем, что тело пустое или содержит строку, а не JSON
        String body = response.getBody().asString();
        System.out.println("Response body: " + body);

    }

    @Test
    @Order(6)
    void getTodoByNonExistentId_ShouldReturnErrorMessageAnd404() {
        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .get("/todos/30") // id, которого нет
                .then()
                .statusCode(404) // Ожидаем 404 Not Found
                .body(matchesJsonSchemaInClasspath("schemas/error_response_id.json")) // Проверяем JSON схему ошибки
                .extract()
                .response();

        String body = response.asString();
        assertTrue(body.contains("Could not find an instance with todos/30"),
                "Тело ответа должно содержать сообщение об ошибке для несуществующего todo.");
    }

    @Test
    @Order(7)
    void createTodo_ShouldReturnTodoWithCorrectStructure() {
        String title = "create todo file";
        String description = "";
        boolean doneStatus = true;

        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .contentType("application/json")
                .body("{\"title\":\"" + title + "\", \"description\":\"" + description + "\", \"doneStatus\":" + doneStatus + "}")
                .when()
                .post("/todos")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/todo_response.json")) // Валидация по JSON схеме
                .extract();

        // Дополнительно проверим, что поля совпадают с отправленными (кроме id)
        var json = response.jsonPath();
        assertEquals(title, json.getString("title"));
        assertEquals(description, json.getString("description"));
        assertEquals(doneStatus, json.getBoolean("doneStatus"));
        assertTrue(json.getInt("id") > 0, "id должен быть больше 0");
    }

    @Test
    @Order(8)
    void createTodo_InvalidDoneStatus_ShouldReturnParseOrValidationError() {
        String badRequestBody = "{ \"title\": \"create todo, a title\", \"doneStatus\": \"bob\", \"description\": \"\" }";

        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .contentType("application/json")
                .body(badRequestBody)
                .when()
                .post("/todos")
                .then()
                .statusCode(400)
                .extract()
                .response();

        String body = response.asString();

        // Разрешаем три вида ошибок:
        boolean isParseError = body.contains("Invalid Json Payload") || body.contains("syntax of the request body");
        boolean isValidationError = body.contains("Failed Validation: doneStatus should be BOOLEAN")
                || body.toLowerCase().contains("doneStatus should be boolean".toLowerCase());

        assertTrue(isParseError || isValidationError,
                "Ответ должен содержать сообщение о некорректном JSON или ошибке типа doneStatus: " + body);
    }

    @Test
    @Order(9)
    void updateTodo_Put_ShouldReturnUpdatedTodo() {
        int todoId = 2;
        String updatedTitle = "PUT todo file";

        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .contentType("application/json")
                .body("{\"title\":\"" + updatedTitle + "\"}")
                .when()
                .put("/todos/" + todoId)
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/todo_response.json")) // Проверка по JSON схеме
                .extract();

        var json = response.jsonPath();
        assertEquals(todoId, json.getInt("id"), "id должен совпадать");
        assertEquals(updatedTitle, json.getString("title"), "title должен быть обновлён");

    }

    @Test
    @Order(10)
    void putTodo_NonExistentId_ShouldReturnError() {
        int nonExistentId = 999999;
        String requestBody = "{ \"title\": \"create todo file\", \"doneStatus\": true, \"description\": \"\" }";

        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .contentType("application/json")
                .body(requestBody)
                .when()
                .put("/todos/" + nonExistentId)
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error_response.json"))
                .extract()
                .response();

        String body = response.asString();
        assertTrue(body.contains("Cannot create todo with PUT due to Auto fields id"),
                "В ответе должно быть сообщение 'Cannot create todo with PUT due to Auto fields id'. Фактический ответ: " + body);
    }

    @Test
    @Order(11)
    void headTodo_ShouldReturnHeaders_AndEmptyBody() {
        int todoId = 2; // Используй существующий id
        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .head("/todos/" + todoId)
                .then()
                .statusCode(200)
                .extract()
                .response();

        // Проверяем, что пришли стандартные заголовки, например Content-Type
        String contentType = response.getHeader("Content-Type");
        assertNotNull(contentType, "Должен быть Content-Type header");
        assertTrue(contentType.contains("application/json") || contentType.contains("application"), "Content-Type должен быть json или другой");

        // Проверяем, что тело пустое (у HEAD-запроса не должно быть body)
        String body = response.getBody().asString();
        assertTrue(body == null || body.isBlank(), "Body у HEAD запроса должен быть пустым");
    }

    @Test
    @Order(12)
    void patchTodos_ShouldReturn405() {
        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .contentType("application/json")
                .body("{\"title\": \"PATCH todo file\", \"doneStatus\": true, \"description\": \"\"}")
                .when()
                .patch("/todos")
                .then()
                .extract()
                .response();

        // Проверяем статус-код
        assertEquals(405, response.getStatusCode(), "Ожидался статус-код 405 Method Not Allowed");
    }

    @Test
    @Order(13)
    void deleteTodo_ThenVerifyTodoIsDeleted() {
        int todoId = 2; // выбираем id для удаления

        // DELETE
        var deleteResponse = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .delete("/todos/" + todoId)
                .then()
                .extract()
                .response();

        assertEquals(200, deleteResponse.getStatusCode(), "Ожидался статус-код 200 при удалении todo");

        String deleteBody = deleteResponse.getBody().asString();
        assertTrue(deleteBody == null || deleteBody.isBlank(), "Ожидалось пустое тело ответа при удалении");

        // GET — после удаления
        var getResponse = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .get("/todos/" + todoId)
                .then()
                .extract()
                .response();

        assertEquals(404, getResponse.getStatusCode(), "Ожидался статус-код 404 для удалённого todo");

        // проверку на сообщение об ошибке:
        String getBody = getResponse.getBody().asString();
        assertTrue(getBody.contains("Could not find an instance"), "Ожидалось сообщение об ошибке в ответе: " + getBody);
    }

    @Test
    @Order(14)
    void deleteNonExistentTodo_ShouldReturn404AndErrorMessage() {
        int nonExistentId = 99999; // несуществующий id

        var response = given()
                .header("X-Challenger", xChallengerHeader)
                .when()
                .delete("/todos/" + nonExistentId)
                .then()
                .extract()
                .response();

        // Проверяем статус-код
        assertEquals(404, response.getStatusCode(), "Ожидался статус-код 404 для несуществующего todo");

        // Проверяем, что тело ответа содержит сообщение об ошибке
        String body = response.getBody().asString();
        assertTrue(body.contains("Could not find any instances"),
                "Ожидалось сообщение об ошибке, фактическое тело: " + body);
    }

}