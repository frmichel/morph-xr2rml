package fr.unice.i3s.morph.xr2rml.mongo

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.seqAsJavaList

import org.bson.Document
import org.jongo.Jongo
import org.junit.Test

import com.mongodb.Block
import com.mongodb.MongoClient
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress

class JongoTest {

    var start: Long = 0
    var end: Long = 0
    val userName = "user"
    val dbName = "test"
    val userPwd = "user"
    val collecName = "test"

    val creds = MongoCredential.createCredential(userName, dbName, userPwd.toCharArray())
    val dbAdr = new ServerAddress("localhost", 27017)
    val mongoClient: MongoClient = new MongoClient(List(dbAdr), List(creds))

    // Define the Jongo context
    val jongoHandler: JongoResultHandler = new JongoResultHandler
    val jongoCtx = new Jongo(mongoClient.getDB(dbName))

    /**
     * Execute a Jongo query given as a simple query string
     */
    private def execJongo(queryStr: String, display: Boolean) = {
        val start = System.currentTimeMillis
        var results: org.jongo.MongoCursor[String] = jongoCtx.getCollection(collecName).find(queryStr).map[String](jongoHandler)
        print(results.toList)
    }

    /* @Test def testQ0() {
        println("------------- testQ0 -------------")

        var qStr = "{'Lastname': 'O\\'Connel'}"

        qStr = """{"Lastname": "O'Connel"}"""
        println(qStr)
        execJongo(qStr, true)
    }

    @Test def test1() {
        println("------------- test1 -------------")

        var src = """{\"adminLevel\": \"Collectivité d'outre-mer\"}"""
        println(src)
        println(src.replaceAll("""\\"""", "\""))
    } */
}
