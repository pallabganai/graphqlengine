package com.example.graphqlengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EnableReactiveMethodSecurity
@SpringBootApplication
public class GraphqlengineApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraphqlengineApplication.class, args);
	}

	@Bean
	MapReactiveUserDetailsService authentication() {
		var users = Map.of(
				"pallab", new String[] {"USER"},
				"arnold", new String[] {"ADMIN"},
				"steve", new String[] {"ADMIN", "USER"})
				.entrySet()
				.stream()
				.map(stringEntry -> User.withDefaultPasswordEncoder()
						.username(stringEntry.getKey())
						.password("pass")
						.roles(stringEntry.getValue())
						.build()
				)
				.toList();

		return new MapReactiveUserDetailsService(users);
	}

	@Bean
	SecurityWebFilterChain authorization(ServerHttpSecurity httpSecurity) {
		return httpSecurity
				.csrf(csrfSpec -> csrfSpec.disable())
				.authorizeExchange(authorizeExchangeSpec -> authorizeExchangeSpec.anyExchange().permitAll())
				.httpBasic(Customizer.withDefaults())
				.build();
	}

	/*@Bean
	SecurityWebFilterChain securityWebFilterChain() {
		return new SecurityWebFilterChain() {
			@Override
			public Mono<Boolean> matches(ServerWebExchange exchange) {
				return Mono.just(false);
			}

			@Override
			public Flux<WebFilter> getWebFilters() {
				return Flux.empty();
			}
		};
	}*/

	@Bean
	RuntimeWiringConfigurer runtimeWiringConfigurer(CrmService crmService) {
		return builder -> builder
				.type("Query", wiring -> wiring
					.dataFetcher("customerById", environment -> crmService.getCustomerById(
							Integer.parseInt(environment.getArgument("id"))
					))
					.dataFetcher("customers", environment -> crmService.getCustomers())
					.dataFetcher("profileById", environment -> crmService.getProfile(
							Integer.parseInt(environment.getArgument("id"))
					))
				)
				.type("Customer", wiring -> wiring
					.dataFetcher("profile", environment -> crmService.getProfile(
							environment.getSource()
					)));
	}
}

record Customer (Integer id, String name) {}

record Profile(Integer id, Integer customerId) {}

@Service
class CrmService {
	@Secured("ROLE_USER")
	Mono<Customer> getCustomerById(Integer id) {
		return Mono.just(new Customer(id, Math.random() > 0.5 ? "A": "B"));
	}
	@PreAuthorize("hasRole('ADMIN')")
	Flux<Customer> getCustomers() {
		return Flux.fromStream(List.of(new Customer(1, "A"),
				new Customer(2, "B")).stream());
	}

	Profile getProfile(Customer customer) {
		return new Profile(customer.id(), customer.id());
	}

	Profile getProfile(int id) {
		return new Profile(id, Math.random() > 0.5 ? 3: 4);
	}
}

@Controller
class MyController {
	@QueryMapping
	public String hello() {
		System.out.println("Hello....");

		return "Hellowwww.............";
	}

	@QueryMapping
	public String helloWithName(@Argument String name) {
		return "Hello..." +name;
	}
}

record Department(Integer id, String departmentName) {}
record Student(Integer id, String name) {}

@Controller
class StudentController {
	private AtomicInteger atomicInteger = new AtomicInteger();

	private List<Student> studentList = new ArrayList<>();

	private List<Department> departmentMapping = new ArrayList<>();

	public StudentController() {
		var a= new Student(atomicInteger.incrementAndGet(), "A Student");
		var b= new Student(atomicInteger.incrementAndGet(), "B Student");
		var c= new Student(atomicInteger.incrementAndGet(), "C Student");

		Arrays.asList(a, b, c).forEach(studentList::add);
		Arrays.asList(a, b, c).forEach(
				student -> departmentMapping.add(new Department(student.id(), Math.random() > 0.5 ? "Maths": "Geo")));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@QueryMapping
	public Flux<Student> getStudents() {
		return Flux.fromIterable(studentList);
	}

	@BatchMapping
	public Map<Student, Department> department(List<Student> students) {
		System.out.println("getDepartment called");
		//students.forEach(System.out::println);
		//departmentMapping.forEach(System.out::println);

		return studentList
				.stream()
				.collect(Collectors.toMap(
						student -> student,
						student -> departmentMapping.stream()
								.filter(department -> department.id() == student.id())
								.findFirst()
								.get()
				));
	}

/*	@SchemaMapping(typeName = "Student", field = "department")
	public Mono<Department> getDepartment(Student student) {
		System.out.println("getDepartment called");

		return Mono.just(departmentMapping
				.stream()
				.filter(department -> department.id() == student.id())
				.findFirst()
				.get());
	}*/

	@MutationMapping
	public Student addStudent(@Argument String studentName) {
		var student = new Student(atomicInteger.incrementAndGet(), studentName);

		studentList.forEach(System.out::println);
		System.out.println(student);

		studentList.add(student);
		departmentMapping.add(new Department(student.id(), Math.random() > 0.5 ? "Maths": "Geo"));

		return student;
	}

	@Controller
	public class GreetingsController {
		@QueryMapping
		public Greeting greeting() {
			return new Greeting("Hello...");
		}

		@SubscriptionMapping
		public Flux<Greeting> greetings() {
			return Flux
					.fromStream(Stream.generate(() -> new Greeting("Hello from stream @ " + Instant.now())))
					.delayElements(Duration.ofSeconds(1))
					.take(10);
		}

		record Greeting(String greeting) {}
	}
}