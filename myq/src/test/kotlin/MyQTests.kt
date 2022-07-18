import com.github.diamondminer88.myq.MyQ
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class MyQTests {
	private val myQ = MyQ()

	@Test
	@Order(1)
	fun `login with email + password`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.login(System.getenv("email"), System.getenv("password"))
			}
		}
	}

	@Test
	@Order(2)
	fun `login with refresh token and refresh access token`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.refreshToken()
			}
		}
	}

	@Test
	@Order(3)
	fun `refresh accounts`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.refreshAccounts()
			}
		}
	}

	@Test
	fun `fetch devices`() {
		assertDoesNotThrow {
			runBlocking {
				myQ.fetchDevices()
			}
		}
	}
}
