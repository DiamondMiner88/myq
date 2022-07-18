import com.github.diamondminer88.myq.MyQ
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test

internal class SampleTest {
	@Test
	fun run() {
		assertDoesNotThrow {
			runBlocking {
				MyQ().login(System.getenv("email"), System.getenv("password"))
			}
		}
	}
}
