package DataCenter

/**
 * Created by leon on 11/30/15.
 */

class Group(val name: String, val description: String, val owner: String) {
  val groupId: String = java.util.UUID.randomUUID().toString
  val admins: collection.mutable.Set[String] = collection.mutable.Set[String]()
  val members: collection.mutable.Set[String] = collection.mutable.Set[String]()
  val albums: collection.mutable.Set[String] = collection.mutable.Set[String]()
}
