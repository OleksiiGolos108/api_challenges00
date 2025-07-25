package tests;

import helpers.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.junit.jupiter.api.BeforeAll;

public class TestBase {

    static String url = ConfigReader.get("challengesURL");

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = url;
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

}
