document.onreadystatechange = function() {
  if(document.readyState == "complete") {
    const items = document.querySelectorAll(".copyable");
    items.forEach(item => {
      item.onclick = function() {
        var range = document.createRange();
  	    range.selectNode(item);
  	    window.getSelection().addRange(range);
        document.execCommand('copy');
        window.getSelection().removeRange(range);
        console.log('Copyed to clipboard');
      };
    });
  }
};
