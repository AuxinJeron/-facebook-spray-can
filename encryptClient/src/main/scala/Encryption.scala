import scala.util.Random

/**
 * Created by leon on 12/16/15.
 */
package protocol {

import annotation.implicitNotFound

import java.io.{DataOutputStream, ByteArrayOutputStream}

@implicitNotFound(msg = "Could not find a Writes for ${T}")
trait Writes[T] {

  def writes(value: T): Array[Byte]
}

class DataOutputStreamWrites[T](writeValue: (DataOutputStream, T) => Unit) extends Writes[T] {

  def writes(value: T): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val dos = new DataOutputStream(bos)
    writeValue(dos, value)
    dos.flush()
    val byteArray = bos.toByteArray
    bos.close()
    byteArray
  }
}

object defaults {
  implicit object WritesString extends Writes[String] {
    def writes(value: String) = value.getBytes("UTF-8")
  }
  implicit object WritesLong extends DataOutputStreamWrites[Long](_.writeLong(_))
  implicit object WritesInt extends DataOutputStreamWrites[Int](_.writeInt(_))
  implicit object WritesShort extends DataOutputStreamWrites[Short](_.writeShort(_))
}
}

package crypto {

import protocol.Writes

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher

trait Encryption {
  def encrypt(dataBytes: Array[Byte], secret: String): Array[Byte]
  def decrypt(codeBytes: Array[Byte], secret: String): Array[Byte]

  def encrypt[T:Writes](data: T, secret: String): Array[Byte] = encrypt(implicitly[Writes[T]].writes(data), secret)
}

class JavaCryptoEncryption(algorithmName: String) extends Encryption {

  def encrypt(bytes: Array[Byte], secret: String): Array[Byte] = {
    val secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), algorithmName)
    val encipher = Cipher.getInstance(algorithmName + "/ECB/PKCS5Padding")
    encipher.init(Cipher.ENCRYPT_MODE, secretKey)
    encipher.doFinal(bytes)
  }

  def decrypt(bytes: Array[Byte], secret: String): Array[Byte] = {
    val secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), algorithmName)
    val encipher = Cipher.getInstance(algorithmName + "/ECB/PKCS5Padding")
    encipher.init(Cipher.DECRYPT_MODE, secretKey)
    encipher.doFinal(bytes)
  }
}

object DES extends JavaCryptoEncryption("DES")
object AES extends JavaCryptoEncryption("AES")

class RSA(val keySize: Int) {

  if(keySize <= 0 ) {
    throw new NumberFormatException("Invalid number!")
  }
  val bitLength = keySize/8
  val p = generatePrime(keySize/2)
  val q = generatePrime(keySize/2)
  val phi = (p.-(BigInt(1))).*(q.-(BigInt(1)))
  val n = p.*(q)

  var publicKey = BigInt(3)
  publicKey = generatePublicKey()
  val privateKey = publicKey.modInverse(phi)

  def generatePublicKey(): BigInt = {
    while (phi.gcd(publicKey).intValue() > 1) {
      publicKey = publicKey.+(BigInt(2))
    }
    publicKey
  }

  def generatePrime(bitLength: Int): BigInt = {
    BigInt.probablePrime(bitLength, Random)
  }

  def encrypt(message: BigInt): BigInt = {
    message.modPow(publicKey, n)
  }

  def decrypt(cipher: BigInt): BigInt = {
    cipher.modPow(privateKey, n)
  }

  def encrypt(message: Array[Byte]): Array[BigInt] = {
    message.map(x => Array[Byte](x)).map(enc)
  }

  def enc(message: Array[Byte]): BigInt = {
    BigInt(message).modPow(publicKey, n)
  }

  def decrypt(cipher: Array[BigInt]): Array[Byte] = {
    cipher.map(decrypt).map(_.toByteArray).flatMap(x => x)
  }

  override def toString() : String = {
    println("p: " + p)
    println("q: " + q)
    println("phi:" + phi)
    println("n: " + n)
    println("public: " + publicKey)
    println("private: " + privateKey)
    "RSA Algorithm in " + keySize + " bits."
  }
}
}

