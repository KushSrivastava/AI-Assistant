import os

def append_deps(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    deps = """
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-cache</artifactId>
		</dependency>
		<dependency>
			<groupId>com.github.ben-manes.caffeine</groupId>
			<artifactId>caffeine</artifactId>
		</dependency>
"""
    if "caffeine" not in content:
        content = content.replace("</dependencies>", deps + "\t</dependencies>")
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)

append_deps('knowledge-bot-cli/pom.xml')
append_deps('knowledge-bot-web/pom.xml')
