package bitnymWallet;
import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bitcoinj.core.ECKey;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;


public class CLTVScriptPairTest {
	
	private CLTVScriptPair csp;
	private Class<CLTVScriptPair> a;
	
	@Before
	public void setUp() throws Exception {
		this.a = (Class<CLTVScriptPair>) (new CLTVScriptPair()).getClass();
	}

	@Test
	public void test() {
		//use reflection to test private method

		Method method = null;
		try {
			method = a.getDeclaredMethod("encodeExpireDate", Long.TYPE);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		method.setAccessible(true);
		long expireTime = 278;
		byte[] result = null;
		try {
			result = (byte[]) method.invoke(null, expireTime);
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			e.printStackTrace();
		}
		ByteBuffer bb = ByteBuffer.allocate(3);
		bb.put((byte) 0x02).put((byte) 0x16).put((byte) 0x01);
		System.out.println(Arrays.toString(bb.array()));
		System.out.println(Arrays.toString(result));
		assertEquals(bb.array().length, result.length);
		for(int i=0; i<result.length;i++) {
			assertEquals(bb.array()[i], result[i]);
		}
	}
	

}
