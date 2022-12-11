package com.giuliopulina.todo;

import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.event.annotation.BeforeTestClass;

@SpringBootTest
class TodoApplicationTests {

	@BeforeAll
	public void setup() {
		/* FIXME: hack to make test working on CI */
		System.setProperty("aws.region", "eu-west-2");
	}

	@Test
	void contextLoads() {
	}

}
