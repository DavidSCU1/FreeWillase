package com.freewillase.backend;

import com.freewillase.backend.domain.SysUser;
import com.freewillase.backend.mapper.SysUserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@EnableAsync
@SpringBootApplication
@MapperScan("com.freewillase.backend.mapper")
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner initUser(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
		return args -> {
			if (userMapper.selectCount(null) == 0) {
				SysUser user = SysUser.builder()
						.username("admin")
						.passwordHash(passwordEncoder.encode("admin123"))
						.displayName("Admin User")
						.email("admin@freewillase.com")
						.status("ACTIVE")
						.createdAt(LocalDateTime.now())
						.updatedAt(LocalDateTime.now())
						.build();
				userMapper.insert(user);
				System.out.println("Default admin user created: admin/admin123");
			}
		};
	}
}
