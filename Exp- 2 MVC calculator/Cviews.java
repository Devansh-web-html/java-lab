import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class Cviews extends JFrame {
    JLabel a,b,result;
    JTextField txta,txtb,txtresult;
    JButton add ,sub,mul,div;

    Cviews(){
        setSize(500, 500);
        setLayout(null);
        setTitle("calculator");
        a=new JLabel("enter A");
        a.setBounds(30,30,100,25);
        add(a);
         b=new JLabel("enter B");
        b.setBounds(30,70,100,25);
        add(b);
         
        txta =new JTextField();
        txta.setBounds(150,30,150,25);                
         add(txta);

         txtb =new JTextField();
        txtb.setBounds(150,70,150,25);                
        add(txtb);


        add=new JButton("+");
        add.setBounds(30,120,60,30 );
        add(add);

        sub=new JButton("-");
        sub.setBounds(100,120 ,60 ,30 );
        add(sub);

        mul=new JButton("*");
        mul.setBounds(170,120 ,60 ,30 );
        add(mul);

        div=new JButton("/");
        div.setBounds(240,120 ,60 ,30 );
        add(div);
        

        result =new JLabel("result");
        div.setBounds(30,180 ,100 ,25 );
        add(div);

        txtresult =new JTextField();
        txtresult.setBounds(150, 180, 150, 25);
        txtresult.setEditable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        add(txtresult);

        setVisible(true);




    }
}
