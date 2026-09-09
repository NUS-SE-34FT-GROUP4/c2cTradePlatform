package sg.edu.nus.iss.c2csectrade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("sg.edu.nus.iss.c2csectrade.mapper") // 扫描 Mapper 接口
public class C2cSecTradeApplication {
    public static void main(String[] args) {  // ✅ 正确写法
        SpringApplication.run(C2cSecTradeApplication.class, args);
    }
}