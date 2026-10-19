import java.rmi.Naming;
public class Client {
    public static void main(String[] args){
        try {
            Calculator  obj =(Calculator) Naming.lookup("rmi://localhost/Calculator");
            System.out.println("Addition =" +obj.add(10, 30));
            System.out.println("Subtract =" +obj.subtract(40, 30));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
}
