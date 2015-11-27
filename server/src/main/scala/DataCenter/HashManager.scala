package DataCenter

import java.security.MessageDigest

/**
 * Created by leon on 11/26/15.
 */

object HashManager {
  val consistentM: Int = 12
  val slotsSize: BigInt = BigInt(2).pow(consistentM)
  def getHashInt(value: String): BigInt = {
    val msgDigest = MessageDigest.getInstance("SHA-1")
    msgDigest.update(value.getBytes("ascii"))
    BigInt.apply(msgDigest.digest()).abs
  }
  def getHashString(value: String): String = {
    val msgDigest = MessageDigest.getInstance("SHA-1")
    msgDigest.update(value.getBytes("ascii"))
    msgDigest.digest().
      foldLeft("")((s: String, b: Byte) => s +
      Character.forDigit((b & 0xf0) >> 4, 16) +
      Character.forDigit(b & 0x0f, 16))
  }
}
