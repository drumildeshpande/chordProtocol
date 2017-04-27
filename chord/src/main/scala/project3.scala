/**
 * COP 5615 - Project 3: Implementation of Chord p2p Protocol.
 * @author1 Sumeet Pande UFID 4890-9873
 * @author2 Drumil Deshpande UFID 8359-8265
 */

package main
import java.security.MessageDigest
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import scala.collection.mutable.ArrayBuffer
import akka.actor.ActorRef
import akka.actor.ActorSystem
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import util.Random
import scala.collection.mutable.ListBuffer
import akka.actor.PoisonPill._
import scala.util.Random

case class join(ActNodeArray:Array[Int],index:Int)
case class makeNodeActive(index:Int)
case class updateSucc(actNodeArray:Array[Int],index:Int)
case class passMsg (searchNode:Int,hopCnt : Int)
case class Received (hops:Int)

object project3 {
  
  def main(args: Array[String]) {
        // To check if the user has inputted the correct arguements
        if (args.length != 2) 
        {
          println("Input arguements not entered correctly")
        }
        else
        // Correct Arguements entered.
        {
            println("Input Arguements Entered Correctly. Chord P2P protocol started ")
            var ActiveNodes = args(0).toInt
            var msgs = args(1).toInt            
            var system = ActorSystem("ChordProtocol")
            val Master = system.actorOf(Props(new MasterActor(ActiveNodes,msgs, system)), name = "Master")
            Master ! "start"
        }
      }
}


class MasterActor(ActiveNodes: Int, NumMsgs: Int, Actsys: ActorSystem) extends Actor {
  var m : Int =0
  var totalnodes:Int=0
  var str:String=""
  var ActNodeArray:Array[Int] = new Array[Int](ActiveNodes)
  var totalHops : Int=0
  var msgReceived : Int=0
  def receive = {
      
      //Initiating the master node
      case "start" =>{
        println("Master Node has been Initiated")
        println("The total number of active nodes : " + ActiveNodes)
        m = (math.log(ActiveNodes.toDouble)/math.log(2.toDouble)).ceil.toInt+1
        println("Value of m " +m)
        //Ensuring that the total # of nodes is significantly higher that active nodes.
        totalnodes=Math.pow(2.0, m.toDouble).toInt
        println("Total Number of Nodes in p2p network is " +totalnodes)
        InitializeNodes
      }
      
      //Activating the random active nodes after selection of active random nodes.
      case makeNodeActive(index:Int) =>{
        var actnodeID=ActNodeArray(index)
        var activatenode = "node_"+ actnodeID
        val anode = context.actorSelection("akka://ChordProtocol/user/"+activatenode.toString())
        anode ! join(ActNodeArray,index) 
      }
      
      case "startScheduling" =>{
        //Starts the process of message passing
        for (i<-0 to ActNodeArray.length-1)
        {
          val sourceNode = context.actorSelection("akka://ChordProtocol/user/node_"+ActNodeArray(i).toString())
          sourceNode ! "startCalling"          
        }        
      }
      
      case Received (hops:Int)=>{
        //Each time a worker nodes report hops increment the initial hops ny that hops and 
        //and incerement the number of messages by  1.Process will terminate when the each 
        //worker will all the messages whose number is givenas an input.
        totalHops+=hops
        msgReceived+=1
        if(msgReceived==ActiveNodes*NumMsgs)
        {
          println("Average hops per message: "+(totalHops.toDouble/msgReceived.toDouble))
          println("Total hops: "+totalHops+", "+"Total msg Rec: "+msgReceived)
          println("Total number of active nodes: "+ActiveNodes+", and total num nodes: "+totalnodes)
          Actsys.shutdown
        }
      }
      
     }
  
  def InitializeNodes = {
     //Initializing the nodes.  
     for (i<-0 to totalnodes-1)
     {
       var workerNodes = Actsys.actorOf(Props(new WorkerActor(ActiveNodes,totalnodes,NumMsgs,i,self)),name = "node_"+i.toString)
       workerNodes ! "Initiate"
     }
     JoinNodes
   }
  
  //This method basically selects the random nodes to be joined.The # of random nodes
  //is equal to the number of active nodes.These random nodes are selected by generating
  //random string and truncating upto a certain length to get random numbers.
  def JoinNodes = {
    var i:Int=0
    while(i<=ActiveNodes-1){
      str = Random.alphanumeric.take(25).mkString
      val sha_temp = MessageDigest.getInstance("SHA-1")
      var randshaone:String =sha_temp.digest(str.getBytes).foldLeft("")((s:String, b: Byte) => s + Character.forDigit((b & 0xf0) >> 4, 16) +Character.forDigit(b & 0x0f, 16))
      var trunc_sha1:String =randshaone.substring(0,m-1)
      trunc_sha1 = trunc_sha1.replaceAll("[^0-9]","")
      if(!trunc_sha1.equals(""))
      {
        var NodeID:Int =((trunc_sha1.toLong)%totalnodes).toInt
        if(ActNodeArray contains NodeID )
        {  
          //This means that generated random number already exist so we need to generate 
          // another random. So we do not increment the counter in this case.
        }
        else
        {
          ActNodeArray(i)=NodeID
          i+=1  
        }
      }
    }
    var index:Int=0
    self ! makeNodeActive(index)
  }
}

class WorkerActor(ActiveNodes:Int, totalnodes:Int , NumMsgs:Int ,Nid:Int, Master:ActorRef) extends Actor{
  
  var m = (math.log(ActiveNodes.toDouble)/math.log(2.toDouble)).ceil.toInt+1
  val fingerTable = Array.ofDim[Int](m,3)
  var hopsCount:Int=0
  
  def receive={
    
    case "Initiate"=>{
      //Just Initiating the total no of nodes which will be significantly higher
      //than actual active nodes.
    }
    
    //This case starts to join the active nodes as per the Chord P2P protocol.
    case join (actNodeArray:Array[Int],index:Int)=>{
      //Initializing the finger table for each active node
      for(i<-0 to m-1)
      {
        fingerTable(i)(0) = (Nid+math.pow(2,i).toInt)%totalnodes
        if(i!=m-1)
        {
          fingerTable(i)(1) = (Nid+math.pow(2,i+1).toInt)%totalnodes;
        }
        else
        {
          fingerTable(i)(1) = (Nid+math.pow(2,i+1).toInt)%totalnodes;
        }
      }      
      getSucc(actNodeArray,index)
      notify(actNodeArray,index)
      //Checks if all the active nodes are added to the network. If not adds the remaining nodes,
      // else if all the active nodes are added then starts the process for message passing.
      if(index<actNodeArray.length-1)
        Master ! makeNodeActive(index+1)
      else
      {
        Master ! "startScheduling"
      }
    }
    
    case updateSucc(actNodeArray:Array[Int],index:Int)=>{
      //println("In node: "+Nid)
      var succ:Int = Int.MaxValue
      for(i<-0 to m-1)
      {
        succ = Int.MaxValue
        for(j<-0 to index-1)
        {
          if(actNodeArray(j)>=fingerTable(i)(0))
          {
            if(succ>actNodeArray(j))
            {
              succ=actNodeArray(j)
            }
          }
          else
          {
            if(actNodeArray(j)+totalnodes > fingerTable(i)(0))
            {
              if(succ>actNodeArray(j)+totalnodes)
              {
                succ=actNodeArray(j)+totalnodes
              }  
            }
          }
        }
        if(succ>totalnodes && succ!=Int.MaxValue)
          succ-=totalnodes
        if(succ==Int.MaxValue)
          succ=Nid
        fingerTable(i)(2)=succ
      }
      /* Method to print the fingerTable if required.
       * for(i<-0 to m-1)
      {
        //println("updateSucc->Finger Table: "+fingerTable(i)(0)+" "+fingerTable(i)(1)+" "+fingerTable(i)(2))
      }
      * 
      */
    }
    
    //Each of the active nodes starts sending messages after interval of 1 seconds
    case "startCalling"=> {
      val sch=context.system.scheduler.schedule(0 seconds, 1 seconds,self,"beginCallingProc")
    }
    
    //This method actually selects one of the random nodes from total nodes to which 
    //the message is passed.
    case "beginCallingProc" => {      
      var randNo = Random.nextInt(totalnodes-1)
      while(randNo==Nid)
        randNo = Random.nextInt(totalnodes-1)  
      self ! passMsg (randNo,hopsCount)
    }
    
    //In order to find the the responsoble node to which the message is passed the ith node
    //first checks if the required node belongs to [neighbor,successor] if yes returns the 
    //successor else does a interval search for all the entries in the look [neighbor,next.neighbor]
    //finds the responsible successor and calls the find_successor method for this successor.
    case passMsg(searchNode:Int,hopCnt:Int)=>{
      //println("Message Passing Startred for Node :" + Nid)      
      var respNode:Int = Int.MaxValue     
      var i:Int=0
      while(i<m && respNode==Int.MaxValue)
      {
        if(fingerTable(i)(0) < fingerTable(i)(2))
        {
           if(searchNode >= fingerTable(i)(0) &&  searchNode <= fingerTable(i)(2))
              respNode=fingerTable (i)(2)
        }
        else if (fingerTable(i)(0)==fingerTable(i)(2))
        {
          if(searchNode==fingerTable(i)(0))
            respNode=fingerTable (i)(0)
        }
        else 
        {
          if (fingerTable(i)(0)<=searchNode)
            respNode=fingerTable (i)(2)
          else if (searchNode>=0 && searchNode<=fingerTable(i)(2))
            respNode=fingerTable (i)(2)
        }
        i+=1
      }
      //This means the required next node is not present in the [neighbor,successor]
      if(respNode==Int.MaxValue)
      {
        //call the iterative interval search method
        self ! intervalSearch(searchNode,hopCnt)
      }
      //Reqd Successor found without any hops.
      else
      {
       Master ! Received(hopCnt)
      }      
    }   
  }
  
  //Once the neighbor aand the neighborinterval is known this method 
  def getSucc(actNodeArray:Array[Int],index:Int)
  {
    //println("In node: "+Nid)
    var succ:Int = Int.MaxValue
    for(i<-0 to m-1)
    {
      succ = Int.MaxValue
      for(j<-0 to index)
      {
        if(actNodeArray(j)>=fingerTable(i)(0))
        {
          if(succ>actNodeArray(j))
          {
            succ=actNodeArray(j)
          }
        }
        else
        {
          if(actNodeArray(j)+totalnodes > fingerTable(i)(0))
          {
            if(succ>actNodeArray(j)+totalnodes)
            {
              succ=actNodeArray(j)+totalnodes
            }  
          }
        }
      }
      if(succ>totalnodes && succ!=Int.MaxValue)
        succ-=totalnodes
      if(succ==Int.MaxValue)
        succ=Nid
      fingerTable(i)(2)=succ
    }
    /* Method to Print the finger table if required.
    for(i<-0 to m-1)
    {
      //println("getSucc->Finger Table: "+fingerTable(i)(0)+" "+fingerTable(i)(1)+" "+fingerTable(i)(2))
    }
    */
  }
  
  // Once a new node is added to the network it should inform the existing active nodes that it
  // has joined the network, so that they can also update their fingerTable considering the newly
  // added node.
  def notify(actNodeArray:Array[Int],index:Int)
  {
    for(i<-0 to index-1)
    {
      var notifyNode = "node_"+ actNodeArray(i)
      //println("notifying node: "+notifyNode.toString)
      val node = context.actorSelection("akka://ChordProtocol/user/"+notifyNode.toString())
      node ! updateSucc(actNodeArray,index+1) 
    }
  }
  
  def intervalSearch(randNode:Int,hopCnt:Int)
  {
    var nextNode:Int = Int.MaxValue
    var i:Int=0;
    while(i<m && nextNode==Int.MaxValue)
    {
      if (fingerTable(i)(0) < fingerTable(i)(1))
      {
        if(randNode >= fingerTable(i)(0) &&  randNode < fingerTable(i)(1))
            nextNode=fingerTable (i)(2)
      }
      else
      {
        if (fingerTable(i)(0)<=randNode)
          nextNode=fingerTable (i)(2)
        else if (randNode>=0 && randNode< fingerTable(i)(1))
          nextNode=fingerTable (i)(2)
      }
      i+=1;
    }
    val nextHopNode = context.actorSelection("akka://ChordProtocol/user/node_"+nextNode.toString())
    nextHopNode ! passMsg(randNode,hopCnt+1)     
  }
}
