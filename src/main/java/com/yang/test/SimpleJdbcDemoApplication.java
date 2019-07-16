package geektime.spring.data.simplejdbcdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication
@Slf4j
@Transactional
public class SimpleJdbcDemoApplication implements CommandLineRunner {

    static volatile boolean rollBackFlag = false;
    @Autowired
    private FooDao fooDao;
    @Autowired
    private BatchFooDao batchFooDao;
    @Autowired
    private PersonService personService;

    public static void main(String[] args) {
        SpringApplication.run(SimpleJdbcDemoApplication.class, args);
    }

    @Autowired
    public ApplicationContext ap;
    @Bean
    @Autowired
    public SimpleJdbcInsert simpleJdbcInsert(JdbcTemplate jdbcTemplate) {
        return new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("FOO").usingGeneratedKeyColumns("ID");
    }

    @Bean
    @Autowired
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) throws Exception {
        test();
    }
    public void test() throws InterruptedException, ExecutionException, FileNotFoundException {
        CountDownLatch cdl = new CountDownLatch(5);
        CountDownLatch cdlMain = new CountDownLatch(1);
        ExecutorService es = Executors.newCachedThreadPool();
        List<Future<String>> list = new ArrayList<Future<String>>();
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY );
        PlatformTransactionManager txManager = ap.getBean(PlatformTransactionManager.class);
        TransactionStatus status = txManager.getTransaction(def);
        List<String> list2 = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            Person person = new Person();
            person.setAge(i);
            person.setId(i);
            person.setName("杨春鹏"+i);
            person.setRemark("备注"+i);
            es.submit(new FooRunnableDemo(person,cdl,cdlMain,ap,txManager,status,list2));
//            list.add(future);
        }
        cdl.await();
        for (String string: list2) {
            if(string.equals("false")){
                rollBackFlag = true;
            }
        }
//        for (Future<String> future : list) {
//            if(future.get().equals("false")){
//                rollBackFlag = true;
//            }
//        }

        cdlMain.countDown();
        log.info("等待主线程...");
        Thread.sleep(10000);
        if(!rollBackFlag){
            log.info("main线程开始执行dml...");
            Person personMain = new Person();
            personMain.setId(1001);
            personMain.setName("main");
            personService.addPerson(personMain);
            txManager.commit(status);
            log.info("main事务："+txManager+",def:"+def);
            log.info("main线程结束dml...");
        }





    }

}

