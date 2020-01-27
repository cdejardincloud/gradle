/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.smoketests

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue

class TestRetryPluginSmokeTest extends AbstractSmokeTest {

    @ToBeFixedForInstantExecution
    @Issue('https://plugins.gradle.org/plugin/org.gradle.test-retry')
    def 'test retry plugin'() {
        when:
        sourceFile()
        testSourceFile()
        buildFile << """
            plugins {
                id "java"
                id "org.gradle.test-retry" version "${TestedVersions.testRetryPlugin}"
            }

            ${jcenterRepository()}

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                testImplementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
            }

            test {
                doFirst {
                    file("marker.file").delete()
                }
            
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                }
            }"""

        then:
        def result = runner('test').buildAndFail()
        expectNoDeprecationWarnings(result)

        and:
        result.task(":test").outcome == TaskOutcome.FAILED
        def output = result.output
        output.findAll("flaky\\(\\) FAILED").size() == 1
        output.findAll("failing\\(\\) FAILED").size() == 3
        output.contains("6 tests completed, 4 failed")
    }

    private TestFile testSourceFile() {
        file("src/test/java/org/acme/AcmeTest.java") << """
package org.acme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class AcmeTest {

    @Test
    void successful() {
        new Acme().otherFunctionality();
    }

    @Test
    void flaky() {
        new Acme().functionality();
    }

    @Test
    void failing() {
        fail();
    }
}
        """
    }

    private TestFile sourceFile() {
        file("src/main/java/org/acme/Acme.java") << """
package org.acme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Acme {

    public void functionality() {
        try {
            Path marker = Paths.get("marker.file");
            if (!Files.exists(marker)) {
                Files.write(marker, "mark".getBytes());
                throw new RuntimeException("fail me!");
            }
            Files.write(marker, "again".getBytes());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public void otherFunctionality() {
        System.out.println("I'm doing things");
    }
}
"""
    }

}